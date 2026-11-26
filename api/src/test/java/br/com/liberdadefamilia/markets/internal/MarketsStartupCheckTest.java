package br.com.liberdadefamilia.markets.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** The fake sources are for dev, e2e and tests; production refuses to start with any of them. */
class MarketsStartupCheckTest {

    private static MarketsProperties properties(String osm, String geocoding, String instagram) {
        return new MarketsProperties(
                null,
                new MarketsProperties.Osm(osm, null, null, null, null, null),
                new MarketsProperties.Geocoding(null, geocoding, null, null, null, null),
                new MarketsProperties.Instagram(null, instagram, null, null, null));
    }

    private static MockEnvironment profile(String name) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(name);
        return environment;
    }

    @Test
    void fakeRefusedUnderProd() {
        for (MarketsProperties fake : new MarketsProperties[] {
            properties("fake", "nominatim", "meta"),
            properties("github", "fake", "meta"),
            properties("github", "nominatim", "fake"),
            properties("fake", "fake", "fake"),
        }) {
            assertThatThrownBy(() -> new MarketsStartupCheck(fake, profile("prod")).run(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not allowed in prod");
        }
    }

    @Test
    void realSourcesAreFineUnderProd() {
        assertThatCode(() -> new MarketsStartupCheck(properties(null, null, null), profile("prod")).run(null))
                .doesNotThrowAnyException();
    }

    @Test
    void fakeIsFineOutsideProd() {
        assertThatCode(() -> new MarketsStartupCheck(properties("fake", "fake", "fake"), profile("dev")).run(null))
                .doesNotThrowAnyException();
    }

    @Test
    void defaultsMatchTheDecision() {
        MarketsProperties defaults = new MarketsProperties(null, null, null, null);

        assertThat(defaults.osm().source()).isEqualTo("github");
        assertThat(defaults.osm().maxBytes()).isEqualTo(52_428_800L);
        assertThat(defaults.osm().minRowRatio()).isEqualTo(0.8);
        assertThat(defaults.osm().staleAfter()).isEqualTo(Duration.ofDays(15));
        assertThat(defaults.osm().timeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(defaults.geocoding().dailyLimit()).isEqualTo(20);
        assertThat(defaults.geocoding().enabled()).isTrue();
        assertThat(defaults.instagram().profileEmbeds()).isFalse();
    }
}
