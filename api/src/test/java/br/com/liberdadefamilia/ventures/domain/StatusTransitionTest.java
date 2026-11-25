package br.com.liberdadefamilia.ventures.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class StatusTransitionTest {

    private static final Set<String> ALLOWED = Set.of(
            "PLANNING>ACTIVE",
            "ACTIVE>PAUSED",
            "PAUSED>ACTIVE",
            "PLANNING>CLOSED",
            "ACTIVE>CLOSED",
            "PAUSED>CLOSED",
            "CLOSED>PAUSED");

    @Test
    void followsTheSpecTable() {
        for (VentureStatus from : VentureStatus.values()) {
            for (VentureStatus to : VentureStatus.values()) {
                assertThat(StatusTransition.allowed(from, to))
                        .as(from + " -> " + to)
                        .isEqualTo(ALLOWED.contains(from + ">" + to));
            }
        }
    }

    @Test
    void typesMatchTheSchemaCheck() {
        assertThat(VentureType.values())
                .extracting(Enum::name)
                .containsExactly("FOOD", "DIGITAL_PRODUCT", "CONTENT", "RESALE", "SERVICE", "OTHER");
    }
}
