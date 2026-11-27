package br.com.liberdadefamilia.gamification.internal;

import br.com.liberdadefamilia.gamification.AchievementsEarned;
import br.com.liberdadefamilia.gamification.domain.AchievementKey;
import br.com.liberdadefamilia.gamification.domain.WeeklyStreak;
import br.com.liberdadefamilia.ledger.EntryWeeks;
import br.com.liberdadefamilia.planning.PlanningFacts;
import br.com.liberdadefamilia.ventures.VenturesExport;
import br.com.liberdadefamilia.workspace.MembershipStatus;
import br.com.liberdadefamilia.workspace.Memberships;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.Modules;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grants the achievements a member has earned (D6). Nothing is done with the gamification module off. What is read
 * from another module (Planejar, Projetos) is skipped with that module off. {@code MONTH_IN_BUDGET} is never granted here; only the monthly job does.
 * Writes are idempotent: a key the member already has is neither read again nor written again.
 */
@Service
public class AchievementService {

    /** Stands for a reserve whose cost is unknown, so that no threshold is met. */
    private static final BigDecimal NO_RESERVE = BigDecimal.valueOf(-1);

    private final AchievementRepository repository;
    private final EntryWeeks entryWeeks;
    private final PlanningFacts planning;
    private final VenturesExport ventures;
    private final Modules modules;
    private final Memberships memberships;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    AchievementService(
            AchievementRepository repository,
            EntryWeeks entryWeeks,
            PlanningFacts planning,
            VenturesExport ventures,
            Modules modules,
            Memberships memberships,
            ApplicationEventPublisher events,
            Clock clock) {
        this.repository = repository;
        this.entryWeeks = entryWeeks;
        this.planning = planning;
        this.ventures = ventures;
        this.modules = modules;
        this.memberships = memberships;
        this.events = events;
        this.clock = clock;
    }

    /** Evaluates one member. */
    @Transactional
    public void evaluate(UUID workspaceId, UUID membershipId, boolean silent) {
        if (!modules.isEnabled(workspaceId, ModuleKey.GAMIFICATION)) {
            return;
        }
        memberships.activeMember(workspaceId, membershipId).ifPresent(member -> {
            FamilyFacts facts = new FamilyFacts(workspaceId, member);
            grantFor(member, facts, modulesOff(workspaceId), silent);
        });
    }

    /** Evaluates every active member, reading what belongs to the family only once. */
    @Transactional
    public void evaluateFamily(UUID workspaceId, boolean silent) {
        if (!modules.isEnabled(workspaceId, ModuleKey.GAMIFICATION)) {
            return;
        }
        List<WorkspaceMember> active = memberships.all(workspaceId).stream()
                .filter(summary -> summary.status() == MembershipStatus.ACTIVE)
                .map(summary -> memberships.activeMember(workspaceId, summary.membershipId()))
                .flatMap(Optional::stream)
                .toList();
        if (active.isEmpty()) {
            return;
        }
        // Members share the family's zone and currency, so the Owner's reading of the reserve stands for everyone.
        WorkspaceMember reference = memberships.activeOwner(workspaceId).orElse(active.get(0));
        FamilyFacts facts = new FamilyFacts(workspaceId, reference);
        Set<ModuleKey> off = modulesOff(workspaceId);
        for (WorkspaceMember member : active) {
            grantFor(member, facts, off, silent);
        }
    }

    @Transactional(readOnly = true)
    public List<AchievementRepository.Row> earned(UUID workspaceId, UUID membershipId) {
        return repository.earned(workspaceId, membershipId);
    }

    /** The modules some achievement is read from that the family has switched off. */
    public Set<ModuleKey> modulesOff(UUID workspaceId) {
        Set<ModuleKey> off = EnumSet.noneOf(ModuleKey.class);
        for (AchievementKey key : AchievementKey.values()) {
            key.module()
                    .filter(module -> !off.contains(module) && !modules.isEnabled(workspaceId, module))
                    .ifPresent(off::add);
        }
        return off;
    }

    private void grantFor(WorkspaceMember member, FamilyFacts facts, Set<ModuleKey> off, boolean silent) {
        Set<AchievementKey> held = EnumSet.noneOf(AchievementKey.class);
        repository.earned(member.workspaceId(), member.membershipId()).forEach(row -> held.add(row.key()));
        MemberFacts mine = new MemberFacts(member);
        Instant now = clock.instant();
        boolean granted = false;
        for (AchievementKey key : AchievementKey.values()) {
            if (key == AchievementKey.MONTH_IN_BUDGET
                    || held.contains(key)
                    || !key.earnableBy(member.role())
                    || key.module().filter(off::contains).isPresent()) {
                continue;
            }
            if (qualifies(key, member, facts, mine)
                    && repository.grant(member.workspaceId(), member.membershipId(), key, now, silent)) {
                granted = true;
            }
        }
        if (granted && !silent) {
            events.publishEvent(new AchievementsEarned(member.workspaceId(), member.membershipId()));
        }
    }

    private boolean qualifies(AchievementKey key, WorkspaceMember member, FamilyFacts facts, MemberFacts mine) {
        return switch (key) {
            case FIRST_TRANSACTION -> !mine.mondays().isEmpty();
            case STREAK_4_WEEKS -> mine.longestStreak() >= 4;
            case FIRST_GOAL -> planning.createdAGoal(member.workspaceId(), member.membershipId());
            case GOAL_ACHIEVED -> facts.anyGoalReached();
            case RESERVE_1_MONTH, RESERVE_3_MONTHS, RESERVE_6_MONTHS ->
                facts.reserveMonths().compareTo(BigDecimal.valueOf(key.reserveMonths())) >= 0;
            case FIRST_VENTURE -> ventures.anyStartedBy(member.workspaceId(), member.membershipId());
            case MONTH_IN_BUDGET -> false;
        };
    }

    /** What belongs to the family, read at most once however many members are evaluated. */
    private final class FamilyFacts {

        private final UUID workspaceId;
        private final WorkspaceMember reference;
        private Boolean anyGoalReached;
        private BigDecimal reserveMonths;

        FamilyFacts(UUID workspaceId, WorkspaceMember reference) {
            this.workspaceId = workspaceId;
            this.reference = reference;
        }

        boolean anyGoalReached() {
            if (anyGoalReached == null) {
                anyGoalReached = planning.anyGoalReached(workspaceId);
            }
            return anyGoalReached;
        }

        BigDecimal reserveMonths() {
            if (reserveMonths == null) {
                reserveMonths = planning.reserveMonths(reference).orElse(NO_RESERVE);
            }
            return reserveMonths;
        }
    }

    /** What belongs to one member, read at most once. */
    private final class MemberFacts {

        private final WorkspaceMember member;
        private SortedSet<LocalDate> mondays;

        MemberFacts(WorkspaceMember member) {
            this.member = member;
        }

        SortedSet<LocalDate> mondays() {
            if (mondays == null) {
                mondays = entryWeeks.mondaysWithEntries(member.workspaceId(), member.membershipId(), member.timezone());
            }
            return mondays;
        }

        int longestStreak() {
            LocalDate today = LocalDate.now(clock.withZone(member.timezone()));
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return WeeklyStreak.of(mondays(), monday).longest();
        }
    }
}
