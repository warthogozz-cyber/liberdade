package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.ledger.LedgerFigures;
import br.com.liberdadefamilia.ledger.MonthTotal;
import br.com.liberdadefamilia.planning.ReserveCostChanged;
import br.com.liberdadefamilia.planning.ReserveTargetChanged;
import br.com.liberdadefamilia.planning.domain.ReserveCoverage;
import br.com.liberdadefamilia.planning.domain.ShortHistory;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Currency;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The emergency reserve and how many months of cost it covers (D12). */
@Service
class ReserveService {

    private static final BigDecimal THREE = BigDecimal.valueOf(3);

    /** The largest monthly cost, the same ceiling a transaction has. */
    private static final BigDecimal MAX_COST = new BigDecimal("999999999.99");

    private final ReserveSettingsRepository settings;
    private final LedgerFigures ledger;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ReserveService(
            ReserveSettingsRepository settings, LedgerFigures ledger, ApplicationEventPublisher events, Clock clock) {
        this.settings = settings;
        this.ledger = ledger;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    ReserveView view(WorkspaceMember member) {
        return build(member, settings.find(member.workspaceId()));
    }

    /**
     * Changes the target and the typed monthly cost. Each is audited only when the stored value changes.
     *
     * @throws BudgetRejectedException when the cost is not positive, too large, or not in the family's currency
     */
    @Transactional
    ReserveView update(WorkspaceMember member, UpdateReserveRequest request) {
        Integer requested = request.targetMonths();
        if (request.isEmpty() || requested != null && (requested < 1 || requested > 24)) {
            throw BudgetRejectedException.invalid();
        }
        UUID workspaceId = member.workspaceId();
        ReserveSettingsRepository.Settings before = settings.findForUpdate(workspaceId);
        int target = requested != null ? requested : before.targetMonths();
        BigDecimal cost = before.manualMonthlyCost();
        if (request.manualMonthlyCostGiven()) {
            Money typed = request.manualMonthlyCost();
            cost = typed == null ? null : checked(member, typed);
        }
        boolean targetChanged = target != before.targetMonths();
        boolean costChanged = !sameCost(cost, before.manualMonthlyCost());
        if (targetChanged || costChanged) {
            settings.update(workspaceId, target, cost);
        }
        if (targetChanged) {
            events.publishEvent(new ReserveTargetChanged(workspaceId, member.userId()));
        }
        if (costChanged) {
            events.publishEvent(new ReserveCostChanged(workspaceId, member.userId()));
        }
        return build(member, new ReserveSettingsRepository.Settings(target, cost));
    }

    private static boolean sameCost(@Nullable BigDecimal a, @Nullable BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static BigDecimal checked(WorkspaceMember member, Money money) {
        BigDecimal amount = money.amount();
        if (amount.signum() <= 0
                || amount.compareTo(MAX_COST) > 0
                || amount.stripTrailingZeros().scale() > money.currency().getDefaultFractionDigits()) {
            throw BudgetRejectedException.invalid();
        }
        if (!money.currency().equals(member.baseCurrency())) {
            throw BudgetRejectedException.currency();
        }
        return amount;
    }

    private ReserveView build(WorkspaceMember member, ReserveSettingsRepository.Settings stored) {
        UUID workspaceId = member.workspaceId();
        Currency currency = member.baseCurrency();
        LocalDate today = LocalDate.now(clock.withZone(member.timezone()));
        YearMonth current = YearMonth.from(today);
        BigDecimal balance = ledger.reserveBalanceOn(workspaceId, today);
        boolean hasAccounts = ledger.hasReserveAccounts(workspaceId);
        BigDecimal sum =
                ledger
                        .confirmedExpenseByMonth(workspaceId, current.minusMonths(3), current.minusMonths(1), false)
                        .stream()
                        .map(MonthTotal::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        CostSource source;
        BigDecimal usable;
        BigDecimal manual = stored.manualMonthlyCost();
        if (!ShortHistory.of(ledger.firstConfirmedOn(workspaceId).orElse(null), sum, current)) {
            source = CostSource.HISTORY;
            usable = sum;
        } else if (manual != null) {
            source = CostSource.MANUAL;
            usable = manual.multiply(THREE);
        } else {
            source = CostSource.NONE;
            usable = null;
        }
        ReserveCoverage coverage = ReserveCoverage.of(balance, usable, stored.targetMonths());
        return new ReserveView(
                Money.of(balance, currency),
                hasAccounts,
                coverage.average() == null ? null : Money.of(coverage.average(), currency),
                source,
                coverage.months(),
                stored.targetMonths(),
                coverage.missing() == null ? null : Money.of(coverage.missing(), currency),
                manual == null ? null : Money.of(manual, currency));
    }
}
