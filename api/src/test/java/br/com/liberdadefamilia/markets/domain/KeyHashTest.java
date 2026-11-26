package br.com.liberdadefamilia.markets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KeyHashTest {

    @Test
    void dependsOnTheFamily() {
        NfceKey key = NfceKey.parse(NfceKeyFixtures.make("43", "2611"), YearMonth.of(2026, 11));
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();

        assertThat(KeyHash.of(one, key)).matches("[0-9a-f]{64}").isEqualTo(KeyHash.of(one, key));
        assertThat(KeyHash.of(one, key)).isNotEqualTo(KeyHash.of(two, key));
    }
}
