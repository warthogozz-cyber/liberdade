package br.com.liberdadefamilia.gamification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.Role;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class AchievementKeyTest {

    static Stream<Arguments> rolesByKey() {
        Set<Role> all = Set.of(Role.OWNER, Role.ADULT, Role.YOUTH, Role.VIEWER);
        Set<Role> noViewer = Set.of(Role.OWNER, Role.ADULT, Role.YOUTH);
        Set<Role> noYouth = Set.of(Role.OWNER, Role.ADULT, Role.VIEWER);
        return Stream.of(
                Arguments.of(AchievementKey.FIRST_TRANSACTION, noViewer),
                Arguments.of(AchievementKey.STREAK_4_WEEKS, noViewer),
                Arguments.of(AchievementKey.FIRST_GOAL, Set.of(Role.OWNER, Role.ADULT)),
                Arguments.of(AchievementKey.GOAL_ACHIEVED, all),
                Arguments.of(AchievementKey.RESERVE_1_MONTH, noYouth),
                Arguments.of(AchievementKey.RESERVE_3_MONTHS, noYouth),
                Arguments.of(AchievementKey.RESERVE_6_MONTHS, noYouth),
                Arguments.of(AchievementKey.MONTH_IN_BUDGET, noYouth),
                Arguments.of(AchievementKey.FIRST_VENTURE, Set.of(Role.OWNER, Role.ADULT)));
    }

    @ParameterizedTest
    @MethodSource("rolesByKey")
    @DisplayName("os papéis de cada chave são os de D5")
    void rolesAreThoseOfD5(AchievementKey key, Set<Role> roles) {
        assertThat(key.roles()).isEqualTo(roles);
        for (Role role : Role.values()) {
            assertThat(key.earnableBy(role)).isEqualTo(roles.contains(role));
        }
    }

    @Test
    @DisplayName("YOUTH não ganha reserva nem MONTH_IN_BUDGET")
    void youthEarnsNoReserveNorBudget() {
        assertThat(Arrays.stream(AchievementKey.values()).filter(k -> k.reserveMonths() > 0))
                .isNotEmpty()
                .allMatch(k -> !k.earnableBy(Role.YOUTH));
        assertThat(AchievementKey.MONTH_IN_BUDGET.earnableBy(Role.YOUTH)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(AchievementKey.class)
    @DisplayName("o módulo de cada chave: nenhum nas duas primeiras, Projetos em FIRST_VENTURE, Planejar nas outras")
    void moduleOfEachKey(AchievementKey key) {
        Optional<ModuleKey> expected = switch (key) {
            case FIRST_TRANSACTION, STREAK_4_WEEKS -> Optional.empty();
            case FIRST_VENTURE -> Optional.of(ModuleKey.VENTURES);
            default -> Optional.of(ModuleKey.PLANNING);
        };
        assertThat(key.module()).isEqualTo(expected);
    }

    @Test
    @DisplayName("reserveMonths é 1, 3 e 6 nas de reserva e 0 nas outras")
    void reserveMonths() {
        assertThat(AchievementKey.RESERVE_1_MONTH.reserveMonths()).isEqualTo(1);
        assertThat(AchievementKey.RESERVE_3_MONTHS.reserveMonths()).isEqualTo(3);
        assertThat(AchievementKey.RESERVE_6_MONTHS.reserveMonths()).isEqualTo(6);
        assertThat(AchievementKey.MONTH_IN_BUDGET.reserveMonths()).isZero();
        assertThat(AchievementKey.FIRST_GOAL.reserveMonths()).isZero();
    }

    @Test
    @DisplayName("a ordem de values() é a de D5")
    void orderIsThatOfD5() {
        assertThat(AchievementKey.values())
                .containsExactly(
                        AchievementKey.FIRST_TRANSACTION,
                        AchievementKey.STREAK_4_WEEKS,
                        AchievementKey.FIRST_GOAL,
                        AchievementKey.GOAL_ACHIEVED,
                        AchievementKey.RESERVE_1_MONTH,
                        AchievementKey.RESERVE_3_MONTHS,
                        AchievementKey.RESERVE_6_MONTHS,
                        AchievementKey.MONTH_IN_BUDGET,
                        AchievementKey.FIRST_VENTURE);
    }
}
