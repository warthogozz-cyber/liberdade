package br.com.liberdadefamilia.indicators.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.liberdadefamilia.indicators.domain.IndicatorUnit;
import br.com.liberdadefamilia.indicators.domain.Point;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class IndicatorServiceTest {

    /** Tuesday 2026-11-24, 09:00 in Sao Paulo. */
    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    private final IndicatorRepository repository = mock(IndicatorRepository.class);
    private final IndicatorService service = new IndicatorService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    private void series(@Nullable Instant lastSuccessAt) {
        List<IndicatorSeries> all = new ArrayList<>();
        for (IndicatorCode code : IndicatorCode.values()) {
            all.add(new IndicatorSeries(
                    code, "BCB", "1", IndicatorUnit.PERCENT_YEAR, code.name(), lastSuccessAt, null, 0));
            when(repository.recentPoints(eq(code), anyInt())).thenReturn(List.of());
            when(repository.latestSourceUrl(code)).thenReturn(Optional.empty());
        }
        when(repository.allSeries()).thenReturn(all);
    }

    private void points(IndicatorCode code, Point... points) {
        when(repository.recentPoints(eq(code), anyInt())).thenReturn(Arrays.asList(points));
        when(repository.latestSourceUrl(code)).thenReturn(Optional.of("https://example.test/" + code));
    }

    private static Point point(String date, String value) {
        return new Point(LocalDate.parse(date), new BigDecimal(value));
    }

    private IndicatorView card(IndicatorCode code) {
        return service.list().indicators().stream()
                .filter(c -> c.code().equals(code.name()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void fiveCardsInEnumOrder() {
        series(NOW);

        assertThat(service.list().indicators())
                .extracting(IndicatorView::code)
                .containsExactly("SELIC_TARGET", "CDI", "IPCA_MONTH", "IPCA_12M", "USD_PTAX_SELL");
    }

    @Test
    void noPointMeansNulls() {
        series(null);

        IndicatorView card = card(IndicatorCode.CDI);

        assertThat(card.value()).isNull();
        assertThat(card.referenceDate()).isNull();
        assertThat(card.variation()).isNull();
        assertThat(card.source().url()).isNull();
        assertThat(card.source().name()).isEqualTo("Banco Central do Brasil");
        assertThat(card.updatedAt()).isNull();
        assertThat(card.stale()).isTrue();
    }

    @Test
    void roundsHalfUp() {
        series(NOW);
        points(IndicatorCode.CDI, point("2026-11-24", "15.005"));
        points(IndicatorCode.USD_PTAX_SELL, point("2026-11-24", "5.43215"));

        assertThat(card(IndicatorCode.CDI).value()).isEqualTo("15.01");
        assertThat(card(IndicatorCode.USD_PTAX_SELL).value()).isEqualTo("5.4322");
        assertThat(card(IndicatorCode.CDI).referenceDate()).isEqualTo(LocalDate.parse("2026-11-24"));
        assertThat(card(IndicatorCode.CDI).source().url()).isEqualTo("https://example.test/CDI");
    }

    @Test
    void selicVariationIsTheLastChange() {
        series(NOW);
        points(
                IndicatorCode.SELIC_TARGET,
                point("2026-11-03", "14.50"),
                point("2026-11-04", "14.50"),
                point("2026-11-05", "15.00"),
                point("2026-11-24", "15.00"));

        VariationView variation = card(IndicatorCode.SELIC_TARGET).variation();

        assertThat(variation).isEqualTo(new VariationView("0.50", "PP", "LAST_CHANGE", LocalDate.parse("2026-11-05")));
    }

    @Test
    void dollarVariationInPercent() {
        series(NOW);
        points(IndicatorCode.USD_PTAX_SELL, point("2026-11-23", "5.4321"), point("2026-11-24", "5.4100"));

        VariationView variation = card(IndicatorCode.USD_PTAX_SELL).variation();

        assertThat(variation)
                .isEqualTo(new VariationView("-0.41", "PERCENT", "PREVIOUS", LocalDate.parse("2026-11-23")));
    }

    @Test
    void staleFollowsTheClock() {
        // Wednesday the 18th to Tuesday the 24th: the 19th, 20th, 23rd and 24th are four weekdays.
        series(Instant.parse("2026-11-18T12:00:00Z"));
        assertThat(card(IndicatorCode.CDI).stale()).isTrue();

        // Thursday the 19th: three weekdays, still on time.
        series(Instant.parse("2026-11-19T12:00:00Z"));
        assertThat(card(IndicatorCode.CDI).stale()).isFalse();
    }
}
