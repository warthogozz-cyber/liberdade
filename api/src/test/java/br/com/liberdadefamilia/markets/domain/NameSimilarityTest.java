package br.com.liberdadefamilia.markets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameSimilarityTest {

    @Test
    void jaccardOverWords() {
        assertThat(NameSimilarity.score("Leite Integral Exemplo 1L", "leite exemplo integral"))
                .isEqualTo(0.75);
    }

    @Test
    void accentsAndCaseIgnored() {
        assertThat(NameSimilarity.score("Feijão Exemplo", "FEIJAO exemplo")).isEqualTo(1.0);
    }

    @Test
    void singleLettersIgnored() {
        assertThat(NameSimilarity.score("Arroz Exemplo T 1", "Arroz Exemplo")).isEqualTo(1.0);
    }

    @Test
    void disjointIsZero() {
        assertThat(NameSimilarity.score("Arroz Exemplo", "Leite Teste")).isZero();
    }

    @Test
    void noWordsIsZero() {
        assertThat(NameSimilarity.score("T 1", "T 1")).isZero();
    }
}
