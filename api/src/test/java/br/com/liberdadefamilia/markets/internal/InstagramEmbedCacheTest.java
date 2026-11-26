package br.com.liberdadefamilia.markets.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.support.TestClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InstagramEmbedCacheTest {

    private static final Instant START = Instant.parse("2026-11-25T12:00:00Z");
    private static final String URL = "https://www.instagram.com/p/Exemplo123/";

    private final TestClock clock = new TestClock();

    private InstagramEmbedCache cache() {
        clock.freeze(START);
        return new InstagramEmbedCache(clock, 1000);
    }

    @Test
    void keepsAnEmbedForSevenDays() {
        InstagramEmbedCache cache = cache();
        cache.put(URL, Optional.of("<blockquote class=\"instagram-media\"></blockquote>"));

        clock.freeze(START.plus(Duration.ofDays(7)));
        assertThat(cache.get(URL)).isPresent();

        clock.freeze(START.plus(Duration.ofDays(7)).plusSeconds(1));
        assertThat(cache.get(URL)).isEmpty();
    }

    @Test
    void keepsARefusalForADay() {
        InstagramEmbedCache cache = cache();
        cache.put(URL, Optional.empty());

        clock.freeze(START.plus(Duration.ofDays(1)));
        assertThat(cache.get(URL)).contains(Optional.empty());

        clock.freeze(START.plus(Duration.ofDays(1)).plusSeconds(1));
        assertThat(cache.get(URL)).isEmpty();
    }

    @Test
    void evictsOldestPastLimit() {
        clock.freeze(START);
        InstagramEmbedCache cache = new InstagramEmbedCache(clock, 2);
        cache.put("a", Optional.of("1"));
        cache.put("b", Optional.of("2"));
        cache.put("c", Optional.of("3"));

        assertThat(cache.get("a")).isEmpty();
        assertThat(cache.get("b")).isPresent();
        assertThat(cache.get("c")).isPresent();
    }
}
