package br.com.liberdadefamilia.gamification.domain;

import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.Role;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** The achievements, in the order the screen lists them, with who can earn each and what it needs turned on. */
public enum AchievementKey {
    FIRST_TRANSACTION(Set.of(Role.OWNER, Role.ADULT, Role.YOUTH), null, 0),
    STREAK_4_WEEKS(Set.of(Role.OWNER, Role.ADULT, Role.YOUTH), null, 0),
    FIRST_GOAL(Set.of(Role.OWNER, Role.ADULT), ModuleKey.PLANNING, 0),
    GOAL_ACHIEVED(Set.of(Role.OWNER, Role.ADULT, Role.YOUTH, Role.VIEWER), ModuleKey.PLANNING, 0),
    RESERVE_1_MONTH(Set.of(Role.OWNER, Role.ADULT, Role.VIEWER), ModuleKey.PLANNING, 1),
    RESERVE_3_MONTHS(Set.of(Role.OWNER, Role.ADULT, Role.VIEWER), ModuleKey.PLANNING, 3),
    RESERVE_6_MONTHS(Set.of(Role.OWNER, Role.ADULT, Role.VIEWER), ModuleKey.PLANNING, 6),
    MONTH_IN_BUDGET(Set.of(Role.OWNER, Role.ADULT, Role.VIEWER), ModuleKey.PLANNING, 0),
    FIRST_VENTURE(Set.of(Role.OWNER, Role.ADULT), ModuleKey.VENTURES, 0);

    private final Set<Role> roles;
    private final @Nullable ModuleKey module;
    private final int reserveMonths;

    AchievementKey(Set<Role> roles, @Nullable ModuleKey module, int reserveMonths) {
        this.roles = roles;
        this.module = module;
        this.reserveMonths = reserveMonths;
    }

    public Set<Role> roles() {
        return roles;
    }

    /** The module the achievement is read from, so it only counts with that module on; empty for the ledger ones. */
    public Optional<ModuleKey> module() {
        return Optional.ofNullable(module);
    }

    public boolean earnableBy(Role role) {
        return roles.contains(role);
    }

    /** The months of reserve that earn the achievement; 0 for the others. */
    public int reserveMonths() {
        return reserveMonths;
    }
}
