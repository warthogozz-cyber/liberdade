package br.com.liberdadefamilia.gamification.internal;

import br.com.liberdadefamilia.gamification.AchievementsEarned;
import br.com.liberdadefamilia.gamification.domain.AchievementKey;
import br.com.liberdadefamilia.planning.PlanningFacts;
import br.com.liberdadefamilia.workspace.MembershipStatus;
import br.com.liberdadefamilia.workspace.Memberships;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.Modules;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * On day one of each month, grants {@code MONTH_IN_BUDGET} for the month before to every Owner, Adult and Viewer of a
 * family that set limits and kept all of them (D7). It is never retroactive: only the month that just ended is read.
 * Each family goes in its own transaction, so one failure is logged by its class only and does not stop the others.
 * The family's zone is used by the planning reading; families more than 4h20 behind Sao Paulo would be read before
 * their month ends, which the product, made for Brazil, does not meet.
 */
@Component
public class MonthInBudgetJob {

    private static final Logger log = LoggerFactory.getLogger(MonthInBudgetJob.class);
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final PlanningFacts planning;
    private final AchievementRepository repository;
    private final Modules modules;
    private final Memberships memberships;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate tx;

    MonthInBudgetJob(
            PlanningFacts planning,
            AchievementRepository repository,
            Modules modules,
            Memberships memberships,
            ApplicationEventPublisher events,
            Clock clock,
            PlatformTransactionManager transactions) {
        this.planning = planning;
        this.repository = repository;
        this.modules = modules;
        this.memberships = memberships;
        this.events = events;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    @Scheduled(cron = "0 20 4 1 * *", zone = "America/Sao_Paulo")
    public void run() {
        YearMonth month = YearMonth.now(clock.withZone(SAO_PAULO)).minusMonths(1);
        int failed = 0;
        AtomicInteger granted = new AtomicInteger();
        for (UUID workspaceId : planning.familiesWithBudgets(month)) {
            try {
                tx.executeWithoutResult(status -> granted.addAndGet(grantFamily(workspaceId, month)));
            } catch (RuntimeException failure) {
                failed++;
                // The class only: the exception message could carry a value.
                log.warn(
                        "month in budget job failed for a family: {}",
                        failure.getClass().getName());
            }
        }
        log.info("month in budget job done: {} granted, {} families failed", granted.get(), failed);
    }

    private int grantFamily(UUID workspaceId, YearMonth month) {
        if (!modules.isEnabled(workspaceId, ModuleKey.GAMIFICATION)
                || !modules.isEnabled(workspaceId, ModuleKey.PLANNING)) {
            return 0;
        }
        List<WorkspaceMember> active = memberships.all(workspaceId).stream()
                .filter(summary -> summary.status() == MembershipStatus.ACTIVE)
                .map(summary -> memberships.activeMember(workspaceId, summary.membershipId()))
                .flatMap(Optional::stream)
                .filter(member -> AchievementKey.MONTH_IN_BUDGET.earnableBy(member.role()))
                .toList();
        if (active.isEmpty()) {
            return 0;
        }
        // The limits belong to the family: the Owner's reading stands for everyone.
        WorkspaceMember reference = memberships.activeOwner(workspaceId).orElse(active.get(0));
        if (!planning.withinBudget(reference, month).orElse(false)) {
            return 0;
        }
        Instant now = clock.instant();
        int granted = 0;
        for (WorkspaceMember member : active) {
            if (repository.grant(workspaceId, member.membershipId(), AchievementKey.MONTH_IN_BUDGET, now, false)) {
                granted++;
                events.publishEvent(new AchievementsEarned(workspaceId, member.membershipId()));
            }
        }
        return granted;
    }
}
