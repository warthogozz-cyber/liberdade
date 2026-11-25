package br.com.liberdadefamilia.gamification.domain;

import br.com.liberdadefamilia.workspace.Role;
import java.util.Set;

/** The achievements, in the order the screen lists them, with who can earn each and what it needs turned on. */
public enum AchievementKey {
    FIRST_TRANSACTION(Set.of(Role.OWNER, Role.ADULT, Role.YOUTH), false, 0),
    STREAK_4_WEEKS(Set.of(Role.OWNER, Role.ADULT, Role.YOUTH), false, 0),
    FIRST_GOAL(Set.of(Role.OWNER, Role.ADULT), true, 0),
    GOAL_ACHIEVED(Set.of(Role.OWNER, Role.ADULT, Role.YOUTH, Role.VIEWER), true, 0),
    RESERVE_1_MONTH(Set.of(Role.OWNER, Role.ADULT, Role.VIEWER), true, 1),
    RESERVE_3_MONTHS(Set.of(Role.OWNER, Role.ADULT, Role.VIEWER), true, 3),
    RESERVE_6_MONTHS(Set.of(Role.OWNER, Role.ADULT, Role.VIEWER), true, 6),
    MONTH_IN_BUDGET(Set.of(Role.OWNER, Role.ADULT, Role.VIEWER), true, 0);

    private final Set<Role> roles;
    private final boolean needsPlanning;
    private final int reserveMonths;

    AchievementKey(Set<Role> roles, boolean needsPlanning, int reserveMonths) {
        this.roles = roles;
        this.needsPlanning = needsPlanning;
        this.reserveMonths = reserveMonths;
    }

    public Set<Role> roles() {
        return roles;
    }

    /** True when the achievement is read from Planejar, so it only counts with that module on. */
    public boolean needsPlanning() {
        return needsPlanning;
    }

    public boolean earnableBy(Role role) {
        return roles.contains(role);
    }

    /** The months of reserve that earn the achievement; 0 for the others. */
    public int reserveMonths() {
        return reserveMonths;
    }
}
