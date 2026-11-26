package br.com.liberdadefamilia.markets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CnpjTest {

    @Test
    void numericAndAlphanumeric() {
        assertThat(Cnpj.isValid("12345678000195")).isTrue();
        assertThat(Cnpj.isValid(NfceKeyFixtures.cnpj("12ABC34501DE"))).isTrue();

        assertThat(Cnpj.isValid("11111111111111")).isFalse();
        assertThat(Cnpj.isValid("00000000000000")).isFalse();
        assertThat(Cnpj.isValid("AAAAAAAAAAAAAA")).isFalse();
        assertThat(Cnpj.isValid("12ABC34501DEA5")).isFalse();
        assertThat(Cnpj.isValid("12ABC34501DE5A")).isFalse();
    }

    @Test
    void rejectsWrongDigitsLengthCaseAndNull() {
        assertThat(Cnpj.isValid("12345678000196")).isFalse();
        assertThat(Cnpj.isValid("12345678000185")).isFalse();
        assertThat(Cnpj.isValid("1234567800019")).isFalse();
        assertThat(Cnpj.isValid(
                        "12abc34501de" + NfceKeyFixtures.cnpj("12ABC34501DE").substring(12)))
                .isFalse();
        assertThat(Cnpj.isValid(null)).isFalse();
    }
}
