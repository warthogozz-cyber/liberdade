package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.domain.BalanceCalculator;
import br.com.liberdadefamilia.ledger.domain.CategoryRanking;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.YearMonth;
import java.util.Currency;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The month at a glance. Everything is in the family's base currency; accounts and transactions in other currencies
 * are left out rather than mixed in.
 */
@Service
class DashboardService {

    /** How many categories the panel names before it gathers the rest in "Outras" (D8). */
    private static final int CATEGORY_LIMIT = 6;

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final TransactionService transactionService;

    DashboardService(
            AccountRepository accounts, TransactionRepository transactions, TransactionService transactionService) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.transactionService = transactionService;
    }

    /**
     * One transaction, so that the balance, the totals and the ranking come from the same moment. Every number is of
     * the member's scope (D13): for the youth, the accounts they own.
     */
    @Transactional(readOnly = true)
    DashboardView monthOf(WorkspaceMember member, YearMonth month) {
        transactionService.checkMonth(member, month);
        Currency base = member.baseCurrency();
        AccountScope scope = accounts.scopeFor(member);
        var initialBalances = accounts.activeInitialBalances(member.workspaceId(), scope);
        initialBalances.values().removeIf(money -> !money.currency().equals(base));
        var flows = transactions.flowsUntil(member.workspaceId(), month.atDay(1), month.atEndOfMonth(), scope).stream()
                .filter(flow -> flow.amount().currency().equals(base))
                .toList();
        var summary = BalanceCalculator.summarize(month, base, initialBalances, flows);
        var categories = transactions.expensesByTopCategory(member.workspaceId(), month, scope).stream()
                .filter(total -> total.current().currency().equals(base))
                .toList();
        return new DashboardView(
                month.toString(),
                summary.income(),
                summary.expenses(),
                summary.result(),
                summary.balance(),
                CategoryRanking.rank(categories, CATEGORY_LIMIT).stream()
                        .map(line -> new DashboardCategory(line.categoryId(), line.current(), line.previous()))
                        .toList(),
                summary.daily().stream()
                        .map(day -> new DashboardDay(day.day(), day.balance()))
                        .toList());
    }
}
