package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.ledger.AccountRef;
import br.com.liberdadefamilia.ledger.LedgerFigures;
import br.com.liberdadefamilia.planning.GoalArchived;
import br.com.liberdadefamilia.planning.domain.GoalIcon;
import br.com.liberdadefamilia.planning.domain.SuggestedContribution;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.workspace.Role;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The family's goals and what is put aside for them (D8 to D11). Only archiving is audited; contributions are not.
 */
@Service
class GoalService {

    /** The active goals a family on the Grátis plan may have while selling is on (D11). */
    static final int FREE_ACTIVE_GOALS = 3;

    /** The largest target or contribution, the same ceiling a transaction has. */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final GoalRepository goals;
    private final LedgerFigures ledger;
    private final Plans plans;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    GoalService(
            GoalRepository goals, LedgerFigures ledger, Plans plans, ApplicationEventPublisher events, Clock clock) {
        this.goals = goals;
        this.ledger = ledger;
        this.plans = plans;
        this.events = events;
        this.clock = clock;
    }

    /**
     * The family's goals. It writes: the linked goals whose account reached the target are marked first (D9), which
     * also covers a check after a ledger change that failed.
     */
    @Transactional
    GoalsView list(WorkspaceMember member) {
        UUID workspaceId = member.workspaceId();
        Map<UUID, BigDecimal> balances = new HashMap<>();
        List<GoalRepository.Row> rows = goals.list(workspaceId);
        boolean marked = false;
        for (GoalRepository.Row row : rows) {
            UUID account = row.linkedAccountId();
            if (account != null
                    && row.achievedAt() == null
                    && balanceOf(workspaceId, account, balances).compareTo(row.targetAmount()) >= 0) {
                marked |= goals.markReached(workspaceId, row.id(), clock.instant());
            }
        }
        if (marked) {
            rows = goals.list(workspaceId);
        }
        Map<UUID, BigDecimal> sums = goals.contributionSums(workspaceId);
        YearMonth current = currentMonth(member);
        List<GoalView> views = rows.stream()
                .map(row -> {
                    UUID account = row.linkedAccountId();
                    BigDecimal saved = account != null
                            ? balanceOf(workspaceId, account, balances)
                            : sums.getOrDefault(row.id(), BigDecimal.ZERO);
                    return view(row, saved, member, current);
                })
                .toList();
        return new GoalsView(views, canManage(member) && limitReached(workspaceId));
    }

    /**
     * Creates the goal in the family's base currency.
     *
     * @throws GoalRejectedException when the icon, target, due month or linked account is wrong
     * @throws GoalLimitReachedException when the Grátis limit stops it
     */
    @Transactional
    GoalView create(WorkspaceMember member, CreateGoalRequest request) {
        UUID workspaceId = member.workspaceId();
        GoalIcon icon = iconOf(request.icon());
        Currency base = member.baseCurrency();
        BigDecimal target = checkedTarget(request.targetAmount(), base);
        YearMonth current = currentMonth(member);
        YearMonth due = request.dueOn();
        if (due != null && due.isBefore(current)) {
            throw GoalRejectedException.dueInPast();
        }
        UUID linked = request.linkedAccountId();
        if (linked != null) {
            AccountRef account = ledger.account(workspaceId, linked)
                    .filter(each -> !each.archived())
                    .orElseThrow(GoalRejectedException::linkedAccountInvalid);
            if (!account.currency().equals(base.getCurrencyCode())) {
                throw GoalRejectedException.currency();
            }
        }
        goals.lockFamily(workspaceId);
        if (limitReached(workspaceId)) {
            throw new GoalLimitReachedException();
        }
        UUID id = goals.insert(
                workspaceId,
                request.name(),
                target,
                base.getCurrencyCode(),
                due == null ? null : due.atDay(1),
                icon.key(),
                linked,
                member.membershipId());
        return reachedView(member, goals.find(workspaceId, id).orElseThrow(), current);
    }

    /**
     * Changes the name, target, due month or icon. Lowering the target to what is saved marks the goal reached.
     *
     * @throws GoalNotFoundException when the family has no such active goal
     * @throws GoalRejectedException when a field is wrong or the version is not the goal's
     */
    @Transactional
    GoalView update(WorkspaceMember member, UUID id, UpdateGoalRequest request) {
        UUID workspaceId = member.workspaceId();
        GoalRepository.Row row = goals.find(workspaceId, id).orElseThrow(GoalNotFoundException::new);
        YearMonth current = currentMonth(member);
        String name = request.name() != null ? request.name() : row.name();
        String icon = request.icon() != null ? iconOf(request.icon()).key() : row.icon();
        BigDecimal target = request.targetAmount() != null
                ? checkedTarget(request.targetAmount(), Currency.getInstance(row.currency()))
                : row.targetAmount();
        YearMonth due = request.dueOn();
        if (due != null && due.isBefore(current)) {
            throw GoalRejectedException.dueInPast();
        }
        if (!goals.update(
                workspaceId,
                id,
                name,
                target,
                due != null ? due.atDay(1) : row.dueOn(),
                icon,
                request.version(),
                clock.instant())) {
            throw GoalRejectedException.versionConflict();
        }
        return reachedView(member, goals.find(workspaceId, id).orElseThrow(), current);
    }

    /** Archives the goal; archiving it again changes nothing and is not audited again. */
    @Transactional
    void archive(WorkspaceMember member, UUID id) {
        UUID workspaceId = member.workspaceId();
        if (!goals.archive(workspaceId, id, clock.instant())) {
            if (!goals.exists(workspaceId, id)) {
                throw new GoalNotFoundException();
            }
            return;
        }
        events.publishEvent(new GoalArchived(workspaceId, id, member.userId()));
    }

    /**
     * Puts money into the goal, or takes it out with a negative amount. Reaching the target marks it reached.
     *
     * @throws GoalNotFoundException when the family has no such active goal
     * @throws GoalRejectedException when the amount is wrong, the goal follows an account, or the saved would go
     *     below zero
     */
    @Transactional
    ContributionView contribute(WorkspaceMember member, UUID id, CreateContributionRequest request) {
        UUID workspaceId = member.workspaceId();
        GoalRepository.Row row = goals.findForUpdate(workspaceId, id).orElseThrow(GoalNotFoundException::new);
        Money amount = request.amount();
        BigDecimal value = amount.amount();
        if (value.signum() == 0
                || value.abs().compareTo(MAX_AMOUNT) > 0
                || value.stripTrailingZeros().scale() > amount.currency().getDefaultFractionDigits()) {
            throw GoalRejectedException.invalid();
        }
        if (row.linkedAccountId() != null) {
            throw GoalRejectedException.linkedAccount();
        }
        if (!amount.currency().getCurrencyCode().equals(row.currency())) {
            throw GoalRejectedException.currency();
        }
        BigDecimal saved = goals.contributionSum(workspaceId, id).add(value);
        if (saved.signum() < 0) {
            throw GoalRejectedException.negativeBalance();
        }
        GoalRepository.ContributionRow contribution = goals.insertContribution(
                workspaceId, id, value, request.occurredOn(), member.membershipId(), row.currency());
        if (saved.compareTo(row.targetAmount()) >= 0) {
            goals.markReached(workspaceId, id, clock.instant());
        }
        return contributionView(contribution);
    }

    /** The goal's contributions, newest first. */
    @Transactional(readOnly = true)
    List<ContributionView> contributions(WorkspaceMember member, UUID id) {
        UUID workspaceId = member.workspaceId();
        goals.find(workspaceId, id).orElseThrow(GoalNotFoundException::new);
        return goals.contributions(workspaceId, id).stream()
                .map(GoalService::contributionView)
                .toList();
    }

    /**
     * Marks reached the active goals that follow one of the accounts and whose balance got to the target (D9). It runs
     * in a transaction of its own, after the ledger's committed (D4).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void reachLinked(UUID workspaceId, Collection<UUID> accountIds) {
        Map<UUID, BigDecimal> balances = new HashMap<>();
        for (GoalRepository.Row row : goals.linkedUnreached(workspaceId, accountIds)) {
            UUID account = row.linkedAccountId();
            if (account != null && balanceOf(workspaceId, account, balances).compareTo(row.targetAmount()) >= 0) {
                goals.markReached(workspaceId, row.id(), clock.instant());
            }
        }
    }

    /** Marks the goal reached when what it has saved got to the target, and shows it as it is then. */
    private GoalView reachedView(WorkspaceMember member, GoalRepository.Row row, YearMonth current) {
        UUID workspaceId = member.workspaceId();
        BigDecimal saved = savedOf(workspaceId, row);
        if (row.achievedAt() == null
                && saved.compareTo(row.targetAmount()) >= 0
                && goals.markReached(workspaceId, row.id(), clock.instant())) {
            row = goals.find(workspaceId, row.id()).orElseThrow();
        }
        return view(row, saved, member, current);
    }

    private BigDecimal savedOf(UUID workspaceId, GoalRepository.Row row) {
        UUID account = row.linkedAccountId();
        return account != null
                ? ledger.accountBalance(workspaceId, account)
                : goals.contributionSum(workspaceId, row.id());
    }

    private BigDecimal balanceOf(UUID workspaceId, UUID accountId, Map<UUID, BigDecimal> balances) {
        return balances.computeIfAbsent(accountId, account -> ledger.accountBalance(workspaceId, account));
    }

    /** Whether a new goal would pass the Grátis limit (D11). Counts the goals as they are now. */
    private boolean limitReached(UUID workspaceId) {
        return plans.legalSetEnabled()
                && plans.current(workspaceId).key() == PlanKey.FREE
                && goals.countActive(workspaceId) >= FREE_ACTIVE_GOALS;
    }

    private YearMonth currentMonth(WorkspaceMember member) {
        return YearMonth.now(clock.withZone(member.timezone()));
    }

    private static boolean canManage(WorkspaceMember member) {
        return member.role() == Role.OWNER || member.role() == Role.ADULT;
    }

    private static GoalIcon iconOf(String key) {
        return GoalIcon.fromKey(key).orElseThrow(GoalRejectedException::invalid);
    }

    /** A target above zero, within the ceiling, in cents, and in the currency the goal is kept in. */
    private static BigDecimal checkedTarget(Money target, Currency currency) {
        BigDecimal amount = target.amount();
        if (amount.signum() <= 0
                || amount.compareTo(MAX_AMOUNT) > 0
                || amount.stripTrailingZeros().scale() > target.currency().getDefaultFractionDigits()) {
            throw GoalRejectedException.invalid();
        }
        if (!target.currency().equals(currency)) {
            throw GoalRejectedException.currency();
        }
        return amount;
    }

    private static GoalView view(
            GoalRepository.Row row, BigDecimal rawSaved, WorkspaceMember member, YearMonth current) {
        Currency currency = Currency.getInstance(row.currency());
        BigDecimal target = row.targetAmount();
        BigDecimal saved = rawSaved.max(BigDecimal.ZERO);
        int percent = saved.multiply(HUNDRED)
                .divide(target, 0, RoundingMode.FLOOR)
                .min(HUNDRED)
                .intValueExact();
        YearMonth due = row.dueOn() == null ? null : YearMonth.from(row.dueOn());
        @Nullable
        Money suggested = row.achievedAt() != null
                ? null
                : SuggestedContribution.of(target, saved, due, current)
                        .map(amount -> Money.of(amount, currency))
                        .orElse(null);
        UUID linked = row.linkedAccountId();
        return new GoalView(
                row.id(),
                row.name(),
                row.icon(),
                Money.of(target, currency),
                Money.of(saved, currency),
                percent,
                due,
                suggested,
                linked != null,
                member.role() == Role.YOUTH ? null : linked,
                row.achievedAt(),
                row.version());
    }

    private static ContributionView contributionView(GoalRepository.ContributionRow row) {
        return new ContributionView(
                row.id(),
                Money.of(row.amount(), Currency.getInstance(row.currency())),
                row.occurredOn(),
                row.memberId());
    }
}
