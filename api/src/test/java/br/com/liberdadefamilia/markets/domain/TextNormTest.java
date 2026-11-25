package br.com.liberdadefamilia.markets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextNormTest {

    @Test
    void normalizes() {
        assertThat(TextNorm.normalize("  Leite INT. 1L ")).isEqualTo("leite int 1l");
        assertThat(TextNorm.normalize("Pão-de-Açúcar")).isEqualTo("pao de acucar");
        assertThat(TextNorm.normalize("")).isEmpty();
        assertThat(TextNorm.normalize("!!!")).isEmpty();
    }
}
