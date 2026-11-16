package br.com.liberdadefamilia.workspace.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SuggestedWorkspaceNameTest {

    @Test
    void withoutFamilyName() {
        assertThat(MeController.suggestedName(null)).isEqualTo("Minha família");
    }

    @Test
    void blankFamilyName() {
        assertThat(MeController.suggestedName("  ")).isEqualTo("Minha família");
    }

    @Test
    void withFamilyName() {
        assertThat(MeController.suggestedName(" Teste ")).isEqualTo("Família Teste");
    }

    @Test
    void cutAt60() {
        assertThat(MeController.suggestedName("a".repeat(70))).isEqualTo("Família " + "a".repeat(52));
    }

    /** Cutting between the two halves of an emoji would leave a broken character, so the cut moves back one. */
    @Test
    void cutKeepsSurrogatePairsWhole() {
        String suggestion = MeController.suggestedName("a".repeat(51) + "😀");

        assertThat(suggestion).isEqualTo("Família " + "a".repeat(51));
    }
}
