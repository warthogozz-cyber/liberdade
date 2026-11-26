package br.com.liberdadefamilia.markets.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.markets.domain.InvalidKeyException.Reason;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class NfceKeyTest {

    private static final YearMonth NOW = YearMonth.of(2026, 11);

    private static void assertRejected(String raw, Reason reason) {
        assertThatThrownBy(() -> NfceKey.parse(raw, NOW))
                .isInstanceOfSatisfying(
                        InvalidKeyException.class, e -> assertThat(e.reason()).isEqualTo(reason));
    }

    @Test
    void parsesAValidKey() {
        NfceKey key = NfceKey.parse(NfceKeyFixtures.make("43", "2611"), NOW);

        assertThat(key.uf()).isEqualTo("RS");
        assertThat(key.issuedMonth()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(key.sellerCnpj()).isEqualTo(NfceKeyFixtures.cnpj(NfceKeyFixtures.CNPJ_BASE));
        assertThat(key.digits()).hasSize(44);
    }

    @Test
    void parsesAnAlphanumericCnpj() {
        String raw = NfceKeyFixtures.make("35", "2501", "12ABC34501DE", 2, 77, 1);

        assertThat(NfceKey.parse(raw, NOW).sellerCnpj()).startsWith("12ABC34501DE");
    }

    @Test
    void acceptsSpacesAndDots() {
        String raw = NfceKeyFixtures.make("43", "2611");
        String grouped = raw.replaceAll("(.{4})", "$1 ").trim();

        assertThat(NfceKey.parse(grouped, NOW)).isEqualTo(NfceKey.parse(raw, NOW));
        assertThat(NfceKey.parse(grouped.replace(' ', '.'), NOW)).isEqualTo(NfceKey.parse(raw, NOW));
    }

    @Test
    void rejectsEachReason() {
        String valid = NfceKeyFixtures.make("43", "2611");

        assertRejected(valid.substring(0, 43), Reason.LENGTH);
        assertRejected("a" + valid.substring(1), Reason.LENGTH);
        assertRejected(null, Reason.LENGTH);
        assertRejected(NfceKeyFixtures.make("99", "2611"), Reason.UF);
        assertRejected(NfceKeyFixtures.make("43", "2613"), Reason.MONTH);
        assertRejected(NfceKeyFixtures.make("43", "2600"), Reason.MONTH);
        assertRejected(NfceKeyFixtures.make("43", "0912"), Reason.MONTH);
        assertRejected(NfceKeyFixtures.make("43", "2612"), Reason.MONTH);
        assertRejected(
                valid.substring(0, 19) + (valid.charAt(19) == '0' ? '1' : '0') + valid.substring(20), Reason.CNPJ);
        assertRejected(valid.substring(0, 20) + "55" + valid.substring(22), Reason.MODEL);
        assertRejected(NfceKeyFixtures.withWrongCheckDigit(valid), Reason.CHECK_DIGIT);
    }

    @Test
    void currentMonthIsAccepted() {
        assertThat(NfceKey.parse(NfceKeyFixtures.make("43", "2611"), NOW)).isNotNull();
    }

    @Test
    void checkDigitIsZeroWhenTheRestIsBelowTwo() {
        // 43 zeros: the sum is 0, so the rest is 0 and the digit is 0.
        assertThat(NfceKey.checkDigit("0".repeat(43))).isZero();
    }

    @Test
    void toStringHidesTheKey() {
        NfceKey key = NfceKey.parse(NfceKeyFixtures.make("43", "2611"), NOW);
        String text = key.toString();

        for (int i = 0; i + 6 <= key.digits().length(); i++) {
            assertThat(text).doesNotContain(key.digits().substring(i, i + 6));
        }
    }
}
