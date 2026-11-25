package br.com.liberdadefamilia.gamification.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.gamification.domain.AchievementKey;
import br.com.liberdadefamilia.gamification.domain.Streak;
import br.com.liberdadefamilia.gamification.domain.WeeklyStreak;
import br.com.liberdadefamilia.ledger.EntryWeeks;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.Modules;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The weekly streak and the achievements of the person who asks. */
@RestController
class GamificationController {

    private final AchievementService achievements;
    private final EntryWeeks entryWeeks;
    private final Modules modules;
    private final Clock clock;

    GamificationController(AchievementService achievements, EntryWeeks entryWeeks, Modules modules, Clock clock) {
        this.achievements = achievements;
        this.entryWeeks = entryWeeks;
        this.modules = modules;
        this.clock = clock;
    }

    @GetMapping("/v1/workspaces/{ws}/gamification/streak")
    @RequiresModule(ModuleKey.GAMIFICATION)
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER, YOUTH})
    StreakView streak(WorkspaceMember member) {
        LocalDate today = LocalDate.now(clock.withZone(member.timezone()));
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Streak streak = WeeklyStreak.of(
                entryWeeks.mondaysWithEntries(member.workspaceId(), member.membershipId(), member.timezone()), monday);
        return new StreakView(streak.weeks(), streak.frozenWeeks(), streak.longest());
    }

    /** Evaluates in silence first, so what was earned while nobody was looking is on the list. */
    @GetMapping("/v1/workspaces/{ws}/gamification/achievements")
    @RequiresModule(ModuleKey.GAMIFICATION)
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER, YOUTH})
    @Transactional
    AchievementsView achievements(WorkspaceMember member) {
        achievements.evaluate(member.workspaceId(), member.membershipId(), true);
        Map<AchievementKey, AchievementRepository.Row> earned = new EnumMap<>(AchievementKey.class);
        achievements.earned(member.workspaceId(), member.membershipId()).forEach(row -> earned.put(row.key(), row));
        boolean planningOn = modules.isEnabled(member.workspaceId(), ModuleKey.PLANNING);
        List<AchievementView> items = new ArrayList<>();
        for (AchievementKey key : AchievementKey.values()) {
            AchievementRepository.Row row = earned.get(key);
            if (!key.earnableBy(member.role()) || key.needsPlanning() && !planningOn && row == null) {
                continue;
            }
            items.add(new AchievementView(key, row == null ? null : row.earnedAt(), row != null && row.silent()));
        }
        return new AchievementsView(items);
    }
}
