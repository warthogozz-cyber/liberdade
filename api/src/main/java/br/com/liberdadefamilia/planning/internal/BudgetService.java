package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.ledger.CategoryRef;
import br.com.liberdadefamilia.ledger.CategorySpending;
import br.com.liberdadefamilia.ledger.LedgerFigures;
import br.com.liberdadefamilia.ledger.TransactionKind;
import br.com.liberdadefamilia.planning.domain.BudgetRollup;
import br.com.liberdadefamilia.planning.domain.BudgetUsage;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The family's monthly limits per expense category (D6, D7). Limits are never audited (spec 6.11). */
@Service
class BudgetService {

    /** The largest limit, the same ceiling a transaction has. */
    private static final BigDecimal MAX_LIMIT = new BigDecimal("999999999.99");

    private final BudgetRepository budgets;
    private final LedgerFigures ledger;

    BudgetService(BudgetRepository budgets, LedgerFigures ledger) {
        this.budgets = budgets;
        this.ledger = ledger;
    }

    /** A saved line, and whether the save created it. */
    record Saved(BudgetLineView line, boolean created) {}

    /** The month as {@code YYYY-MM}, or a 400 when it is missing or not a month. */
    static YearMonth parseMonth(@Nullable String text) {
        if (text == null || !text.matches("\\d{4}-\\d{2}")) {
            throw BudgetRejectedException.invalid();
        }
        try {
            return YearMonth.parse(text);
        } catch (DateTimeParseException e) {
            throw BudgetRejectedException.invalid();
        }
    }

    @Transactional(readOnly = true)
    BudgetMonthView month(WorkspaceMember member, YearMonth month) {
        UUID workspaceId = member.workspaceId();
        List<CategorySpending> spending = spendingOf(workspaceId, month);
        List<BudgetLineView> lines = budgets.list(workspaceId, month.atDay(1)).stream()
                .map(row -> line(row, spending))
                .toList();
        return new BudgetMonthView(month, lines);
    }

    /**
     * Creates the limit (no version) or changes it (the line's version).
     *
     * @throws BudgetRejectedException when the limit or currency is wrong, the category is missing or not for
     *     spending, or the version is not the line's
     */
    @Transactional
    Saved set(WorkspaceMember member, YearMonth month, UUID categoryId, SetBudgetRequest request) {
        UUID workspaceId = member.workspaceId();
        Money limit = request.limitAmount();
        BigDecimal amount = limit.amount();
        if (amount.signum() <= 0
                || amount.compareTo(MAX_LIMIT) > 0
                || amount.stripTrailingZeros().scale() > limit.currency().getDefaultFractionDigits()) {
            throw BudgetRejectedException.invalid();
        }
        CategoryRef category =
                ledger.category(workspaceId, categoryId).orElseThrow(BudgetRejectedException::categoryNotFound);
        if (category.kind() != TransactionKind.EXPENSE) {
            throw BudgetRejectedException.categoryNotExpense();
        }
        Currency base = member.baseCurrency();
        if (!limit.currency().equals(base)) {
            throw BudgetRejectedException.currency();
        }
        LocalDate first = month.atDay(1);
        String currency = base.getCurrencyCode();
        Long version = request.version();
        boolean created = version == null;
        boolean written = created
                ? budgets.insertIfAbsent(workspaceId, first, categoryId, amount, currency)
                : budgets.update(workspaceId, first, categoryId, amount, currency, version);
        if (!written) {
            throw BudgetRejectedException.versionConflict();
        }
        BudgetRepository.Row row = budgets.find(workspaceId, first, categoryId).orElseThrow();
        return new Saved(line(row, spendingOf(workspaceId, month)), created);
    }

    /** Removes the limit; removing one that is not there changes nothing. */
    @Transactional
    void delete(WorkspaceMember member, YearMonth month, UUID categoryId) {
        budgets.delete(member.workspaceId(), month.atDay(1), categoryId);
    }

    /** Copies the previous month's limits for the categories that have none this month and are not archived. */
    @Transactional
    CopyBudgetsView copyPrevious(WorkspaceMember member, YearMonth month) {
        return new CopyBudgetsView(
                budgets.copy(member.workspaceId(), month.minusMonths(1).atDay(1), month.atDay(1)));
    }

    private List<CategorySpending> spendingOf(UUID workspaceId, YearMonth month) {
        return ledger.confirmedExpenseByCategory(workspaceId, month.atDay(1), month.atEndOfMonth());
    }

    private static BudgetLineView line(BudgetRepository.Row row, List<CategorySpending> spending) {
        Currency currency = Currency.getInstance(row.currency());
        BigDecimal spent = BudgetRollup.spentFor(row.categoryId(), spending);
        BudgetUsage usage = BudgetUsage.of(spent, row.limitAmount());
        return new BudgetLineView(
                row.categoryId(),
                Money.of(row.limitAmount(), currency),
                Money.of(spent, currency),
                usage.percent(),
                usage.state(),
                usage.overBy() == null ? null : Money.of(usage.overBy(), currency),
                row.version());
    }
}
