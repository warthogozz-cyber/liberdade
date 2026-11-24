package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.ledger.LedgerFigures;
import br.com.liberdadefamilia.ledger.MonthTotal;
import br.com.liberdadefamilia.ledger.ScheduledFlow;
import br.com.liberdadefamilia.ledger.SpendingSplit;
import br.com.liberdadefamilia.planning.domain.Flow;
import br.com.liberdadefamilia.planning.domain.Projection;
import br.com.liberdadefamilia.planning.domain.ProjectionPoint;
import br.com.liberdadefamilia.planning.domain.RecurrenceSchedule;
import br.com.liberdadefamilia.planning.domain.ShortHistory;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The balance projection (D13) and the fixed and variable split (D14). */
@Service
class ProjectionService {

    private static final BigDecimal THREE = BigDecimal.valueOf(3);
    private static final int LAST_POINT = 3;

    private final LedgerFigures ledger;
    private final RecurringRuleRepository rules;
    private final Clock clock;

    ProjectionService(LedgerFigures ledger, RecurringRuleRepository rules, Clock clock) {
        this.ledger = ledger;
        this.rules = rules;
        this.clock = clock;
    }

    /**
     * The balance at the end of this month and of the next three. Every flow counts by what it does to the accounts
     * that are not archived, the same the balance today counts. The recorded flows (planned, and confirmed ones dated
     * ahead) come from the ledger that way. Each active rule adds its dates after today that have no row yet, skipped
     * and confirmed ones being rows: so an occurrence counts once, inside the job's horizon and after it. A rule's
     * occurrence is an income on a counted account, an expense out of one, or a transfer: zero between two counted
     * accounts, and an income or expense when only one end is counted. A rule only on archived accounts counts nothing.
     */
    @Transactional(readOnly = true)
    ProjectionView projection(WorkspaceMember member) {
        UUID workspaceId = member.workspaceId();
        Currency currency = member.baseCurrency();
        LocalDate today = LocalDate.now(clock.withZone(member.timezone()));
        YearMonth current = YearMonth.from(today);
        LocalDate until = current.plusMonths(LAST_POINT).atEndOfMonth();

        BigDecimal balanceToday = ledger.activeBalanceOn(workspaceId, today);
        BigDecimal sum =
                ledger
                        .confirmedExpenseByMonth(workspaceId, current.minusMonths(3), current.minusMonths(1), true)
                        .stream()
                        .map(MonthTotal::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean shortHistory =
                ShortHistory.of(ledger.firstConfirmedOn(workspaceId).orElse(null), sum, current);

        List<Flow> flows = new ArrayList<>();
        for (ScheduledFlow recorded : ledger.openFlowsAfter(workspaceId, today, until)) {
            flows.add(new Flow(recorded.on(), signed(recorded)));
        }
        flows.addAll(computed(member, today, until));

        List<ProjectionPoint> points = Projection.compute(today, balanceToday, flows, shortHistory ? null : sum);
        return new ProjectionView(
                today,
                Money.of(balanceToday, currency),
                !shortHistory,
                shortHistory ? null : Money.of(sum.divide(THREE, 2, RoundingMode.HALF_UP), currency),
                points.stream()
                        .map(point -> new ProjectionPointView(
                                point.monthEnd(),
                                Money.of(point.balance(), currency),
                                Money.of(point.scheduled(), currency),
                                Money.of(point.estimatedSpending(), currency)))
                        .toList());
    }

    /** The month's confirmed spending, split into fixed and variable. */
    @Transactional(readOnly = true)
    SpendingSplitView spendingSplit(WorkspaceMember member, YearMonth month) {
        Currency currency = member.baseCurrency();
        SpendingSplit split = ledger.fixedAndVariable(member.workspaceId(), month.atDay(1), month.atEndOfMonth());
        return new SpendingSplitView(month, Money.of(split.fixed(), currency), Money.of(split.variable(), currency));
    }

    /** The ledger never sends a transfer here: it comes as the income or expense it is for the counted accounts. */
    private static BigDecimal signed(ScheduledFlow flow) {
        return switch (flow.kind()) {
            case INCOME -> flow.amount();
            case EXPENSE -> flow.amount().negate();
            case TRANSFER -> BigDecimal.ZERO;
        };
    }

    /** The occurrences of the active rules in {@code (today, until]} that have no row, each signed by its effect. */
    private List<Flow> computed(WorkspaceMember member, LocalDate today, LocalDate until) {
        UUID workspaceId = member.workspaceId();
        Map<UUID, Boolean> counted = new HashMap<>();
        List<Flow> flows = new ArrayList<>();
        for (RecurringRuleView rule : rules.listActive(workspaceId, member.membershipId(), false)) {
            BigDecimal effect = effect(rule, id -> counted.computeIfAbsent(id, key -> isCounted(workspaceId, key)));
            if (effect.signum() == 0) {
                continue;
            }
            List<LocalDate> dates = RecurrenceSchedule.occurrences(
                    rule.frequency(), rule.interval(), rule.startOn(), rule.endOn(), today.plusDays(1), until);
            if (dates.isEmpty()) {
                continue;
            }
            Set<LocalDate> occupied = ledger.occupiedDates(workspaceId, rule.id());
            for (LocalDate date : dates) {
                if (!occupied.contains(date)) {
                    flows.add(new Flow(date, effect));
                }
            }
        }
        return flows;
    }

    /** Whether the account counts in the family's balance: the family has it and it is not archived. */
    private boolean isCounted(UUID workspaceId, UUID accountId) {
        return ledger.account(workspaceId, accountId)
                .map(account -> !account.archived())
                .orElse(false);
    }

    /** What one occurrence of the rule does to the counted accounts. */
    private static BigDecimal effect(RecurringRuleView rule, Predicate<UUID> counted) {
        BigDecimal amount = rule.amount().amount();
        BigDecimal zero = BigDecimal.ZERO;
        boolean from = counted.test(rule.accountId());
        return switch (rule.kind()) {
            case INCOME -> from ? amount : zero;
            case EXPENSE -> from ? amount.negate() : zero;
            case TRANSFER -> {
                UUID to = rule.toAccountId();
                BigDecimal in = to != null && counted.test(to) ? amount : zero;
                yield from ? in.subtract(amount) : in;
            }
        };
    }
}
