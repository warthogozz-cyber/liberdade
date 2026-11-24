package br.com.liberdadefamilia.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class GoalIconTest {

    @Test
    void hasTheTenKeysInOrder() {
        assertThat(Arrays.stream(GoalIcon.values()).map(GoalIcon::key))
                .containsExactly(
                        "savings", "home", "car", "travel", "education", "health", "gift", "party", "tech", "other");
    }

    @Test
    void roundTripsEveryKey() {
        for (GoalIcon icon : GoalIcon.values()) {
            assertThat(GoalIcon.fromKey(icon.key())).contains(icon);
        }
    }

    @Test
    void unknownNullOrDifferentCaseIsEmpty() {
        assertThat(GoalIcon.fromKey("rocket")).isEmpty();
        assertThat(GoalIcon.fromKey(null)).isEmpty();
        assertThat(GoalIcon.fromKey("Savings")).isEmpty();
    }
}
