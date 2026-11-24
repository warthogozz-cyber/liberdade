package br.com.liberdadefamilia.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.indicators.domain.Point;
import br.com.liberdadefamilia.indicators.internal.FetchedPoint;
import br.com.liberdadefamilia.indicators.internal.IndicatorCode;
import br.com.liberdadefamilia.indicators.internal.IndicatorRepository;
import br.com.liberdadefamilia.indicators.internal.IndicatorSeries;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IndicatorRepositoryIT {

    private static final IndicatorCode CODE = IndicatorCode.CDI;
    private static final Instant AT = Instant.parse("2026-11-24T11:30:00Z");

    @Autowired
    IndicatorRepository repository;

    @Autowired
    JdbcClient jdbc;

    /** The tables are shared with the other ITs: leave them as V18 seeded them. */
    @AfterEach
    void restoreSeed() {
        jdbc.sql("delete from indicator_points").update();
        jdbc.sql("update indicator_series set last_success_at = null, last_failure_at = null, failure_streak = 0")
                .update();
    }

    @BeforeEach
    void clean() {
        jdbc.sql("delete from indicator_points").update();
        jdbc.sql("update indicator_series set last_success_at = null, last_failure_at = null, failure_streak = 0")
                .update();
    }

    private static FetchedPoint point(String date, String value) {
        return new FetchedPoint(LocalDate.parse(date), new BigDecimal(value), "https://example.test/" + date);
    }

    private IndicatorSeries series() {
        return repository.allSeries().stream()
                .filter(s -> s.code() == CODE)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void allSeriesFollowTheEnumOrder() {
        assertThat(repository.allSeries()).extracting(IndicatorSeries::code).containsExactly(IndicatorCode.values());
        assertThat(Arrays.stream(IndicatorCode.values()).count()).isEqualTo(5);
    }

    @Test
    void upsertReplacesTheSameDay() {
        repository.upsertPoints(CODE, List.of(point("2026-11-24", "14.90")), AT);
        repository.upsertPoints(CODE, List.of(point("2026-11-24", "15.00")), AT.plusSeconds(60));

        assertThat(jdbc.sql("select count(*) from indicator_points where series_code = 'CDI'")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
        assertThat(repository.recentPoints(CODE, 5))
                .extracting(Point::value)
                .singleElement()
                .satisfies(v -> assertThat(v).isEqualByComparingTo("15.00"));
        assertThat(repository.lastRefDate(CODE)).contains(LocalDate.parse("2026-11-24"));
        assertThat(repository.lastRefDate(IndicatorCode.IPCA_12M)).isEmpty();
    }

    @Test
    void successResetsTheStreak() {
        repository.markFailure(CODE, AT);
        repository.markFailure(CODE, AT.plusSeconds(10));
        repository.markSuccess(CODE, AT.plusSeconds(20));

        IndicatorSeries s = series();
        assertThat(s.failureStreak()).isZero();
        assertThat(s.lastSuccessAt()).isEqualTo(AT.plusSeconds(20));
        assertThat(s.lastFailureAt()).isEqualTo(AT.plusSeconds(10));
    }

    @Test
    void failureAddsOne() {
        repository.markFailure(CODE, AT);
        repository.markFailure(CODE, AT.plusSeconds(10));

        assertThat(series().failureStreak()).isEqualTo(2);
    }

    @Test
    void recentPointsAscending() {
        repository.upsertPoints(
                CODE,
                List.of(point("2026-11-20", "14.00"), point("2026-11-21", "14.10"), point("2026-11-24", "14.20")),
                AT);

        assertThat(repository.recentPoints(CODE, 2))
                .extracting(Point::refDate)
                .containsExactly(LocalDate.parse("2026-11-21"), LocalDate.parse("2026-11-24"));
        assertThat(repository.latestSourceUrl(CODE)).contains("https://example.test/2026-11-24");
    }
}
