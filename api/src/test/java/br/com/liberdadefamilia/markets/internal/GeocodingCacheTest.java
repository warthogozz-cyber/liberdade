package br.com.liberdadefamilia.markets.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.markets.domain.GeoPoint;
import br.com.liberdadefamilia.support.TestClock;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeocodingCacheTest {

    private static final Instant START = Instant.parse("2026-11-25T12:00:00Z");
    private static final List<GeocodedPlace> PLACES = List.of(new GeocodedPlace(
            new GeoPoint(new BigDecimal("-23.000"), new BigDecimal("-46.000")), "Bairro Exemplo, Cidade Exemplo"));

    private final TestClock clock = new TestClock();

    private GeocodingCache cache() {
        clock.freeze(START);
        return new GeocodingCache(clock, Duration.ofHours(24), 1000);
    }

    @Test
    void keepsAnswersForADay() {
        GeocodingCache cache = cache();
        cache.put("rua exemplo 10", PLACES);

        clock.freeze(START.plus(Duration.ofHours(24)));

        assertThat(cache.get("rua exemplo 10")).contains(PLACES);
    }

    @Test
    void expiresAfterADay() {
        GeocodingCache cache = cache();
        cache.put("rua exemplo 10", PLACES);

        clock.freeze(START.plus(Duration.ofHours(24)).plusSeconds(1));

        assertThat(cache.get("rua exemplo 10")).isEmpty();
    }

    @Test
    void evictsOldestPastLimit() {
        GeocodingCache cache = cache();
        for (int i = 0; i <= 1000; i++) {
            cache.put("rua exemplo " + i, PLACES);
        }

        assertThat(cache.get("rua exemplo 0")).isEmpty();
        assertThat(cache.get("rua exemplo 1")).isPresent();
        assertThat(cache.get("rua exemplo 1000")).isPresent();
    }
}
