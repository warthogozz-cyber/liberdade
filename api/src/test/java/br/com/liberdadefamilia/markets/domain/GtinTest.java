package br.com.liberdadefamilia.markets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GtinTest {

    @Test
    void checkDigits() {
        assertThat(Gtin.isValid("96385074")).isTrue();
        assertThat(Gtin.isValid("4006381333931")).isTrue();
        assertThat(Gtin.isValid("10012345678902")).isTrue();
        assertThat(Gtin.isValid("036000291452")).isTrue();

        assertThat(Gtin.isValid("96385075")).isFalse();
        assertThat(Gtin.isValid("4006381333932")).isFalse();
        assertThat(Gtin.isValid("12345678901")).isFalse();
        assertThat(Gtin.isValid("SEM GTIN")).isFalse();
        assertThat(Gtin.isValid("400638133393X")).isFalse();
        assertThat(Gtin.isValid("")).isFalse();
        assertThat(Gtin.isValid(null)).isFalse();
    }
}
