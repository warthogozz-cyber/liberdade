package br.com.liberdadefamilia.markets.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.markets.domain.GeoPoint;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.workspace.Role;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The family's daily lookups are spent only by calls that get an answer. */
class GeocodeServiceTest {

    private final TestClock clock = new TestClock();
    private final AtomicInteger calls = new AtomicInteger();
    private final UUID workspace = UUID.randomUUID();
    private volatile boolean upstreamFails;

    private final Geocoder geocoder = query -> {
        calls.incrementAndGet();
        if (upstreamFails) {
            throw new GeocodingException("down");
        }
        return List.of(new GeocodedPlace(new GeoPoint(new BigDecimal("-10.000"), new BigDecimal("-50.000")), "Lugar"));
    };

    @BeforeEach
    void freeze() {
        clock.freeze(Instant.parse("2026-11-25T15:00:00Z"));
    }

    private GeocodeService service(Duration minInterval, int dailyLimit) {
        MarketsProperties properties = new MarketsProperties(
                "https://app.test/robo",
                null,
                new MarketsProperties.Geocoding(
                        true, "nominatim", "https://geo.test", minInterval, dailyLimit, Duration.ofSeconds(5)),
                null);
        return new GeocodeService(
                properties,
                geocoder,
                new GeocodingThrottle(clock, d -> {}, minInterval),
                new GeocodingCache(clock, Duration.ofDays(1), 100),
                new GeocodingLimits(clock, dailyLimit));
    }

    private WorkspaceMember member() {
        return new WorkspaceMember(
                workspace,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Role.OWNER,
                ZoneId.of("America/Sao_Paulo"),
                Currency.getInstance("BRL"));
    }

    @Test
    void busyGivesTheLookupBack() {
        GeocodeService busy = service(Duration.ofSeconds(11), 1); // one call already waits more than 10 s
        assertThatThrownBy(() -> busy.search(member(), "rua a")).isInstanceOf(GeocodingBusyException.class);
        assertThatThrownBy(() -> busy.search(member(), "rua b")).isInstanceOf(GeocodingBusyException.class);

        assertThat(calls).hasValue(0);
    }

    @Test
    void upstreamFailureGivesTheLookupBack() {
        GeocodeService service = service(Duration.ofSeconds(2), 1);
        upstreamFails = true;
        assertThatThrownBy(() -> service.search(member(), "rua a")).isInstanceOf(MarketRuleException.class);

        upstreamFails = false;
        assertThat(service.search(member(), "rua b").places()).hasSize(1);
        assertThat(calls).hasValue(2);
    }

    @Test
    void aSuccessCountsOnceAndACacheHitDoesNot() {
        GeocodeService service = service(Duration.ofSeconds(2), 1);

        service.search(member(), "rua a");
        service.search(member(), "rua a"); // cache hit: free
        assertThat(calls).hasValue(1);

        assertThatThrownBy(() -> service.search(member(), "rua b")).isInstanceOf(MarketRuleException.class);
        assertThat(calls).hasValue(1);
    }
}
