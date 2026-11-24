package br.com.liberdadefamilia.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.indicators.internal.FetchedPoint;
import br.com.liberdadefamilia.indicators.internal.IndicatorCode;
import br.com.liberdadefamilia.indicators.internal.IndicatorFetchException;
import br.com.liberdadefamilia.indicators.internal.IndicatorJob;
import br.com.liberdadefamilia.indicators.internal.IndicatorRepository;
import br.com.liberdadefamilia.indicators.internal.IndicatorSeries;
import br.com.liberdadefamilia.indicators.internal.IndicatorSource;
import br.com.liberdadefamilia.indicators.internal.Sleeper;
import br.com.liberdadefamilia.support.TestClock;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@Import({TestcontainersConfiguration.class, IndicatorJobIT.Config.class})
class IndicatorJobIT {

    // 2026-11-24 09:00 in Sao Paulo (UTC-3).
    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    @FunctionalInterface
    interface Answer {
        List<FetchedPoint> give(IndicatorCode code) throws IndicatorFetchException;
    }

    record Window(IndicatorCode code, LocalDate from, LocalDate to) {}

    static class RecordingSource implements IndicatorSource {
        final List<Window> windows = new CopyOnWriteArrayList<>();
        final Map<IndicatorCode, Answer> answers = new EnumMap<>(IndicatorCode.class);
        volatile Runnable beforeAnswer = () -> {};

        @Override
        public List<FetchedPoint> fetch(IndicatorCode code, String ref, LocalDate from, LocalDate to)
                throws IndicatorFetchException {
            windows.add(new Window(code, from, to));
            beforeAnswer.run();
            Answer answer = answers.get(code);
            return answer != null ? answer.give(code) : List.of(valid(code));
        }

        void reset() {
            windows.clear();
            answers.clear();
            beforeAnswer = () -> {};
        }
    }

    static class NotingSleeper implements Sleeper {
        final List<Duration> waits = new CopyOnWriteArrayList<>();

        @Override
        public void sleep(Duration duration) {
            waits.add(duration);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            TestClock clock = new TestClock();
            clock.freeze(NOW);
            return clock;
        }

        @Bean
        @Primary
        RecordingSource recordingSource() {
            return new RecordingSource();
        }

        @Bean
        @Primary
        NotingSleeper notingSleeper() {
            return new NotingSleeper();
        }
    }

    @Autowired
    IndicatorJob job;

    @Autowired
    IndicatorRepository repository;

    @Autowired
    RecordingSource source;

    @Autowired
    NotingSleeper sleeper;

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
        source.reset();
        sleeper.waits.clear();
    }

    private static FetchedPoint valid(IndicatorCode code) {
        BigDecimal value = code == IndicatorCode.USD_PTAX_SELL ? new BigDecimal("5.4321") : new BigDecimal("1.23");
        return new FetchedPoint(LocalDate.parse("2026-11-24"), value, "https://example.test/" + code);
    }

    private long pointsOf(IndicatorCode code) {
        return jdbc.sql("select count(*) from indicator_points where series_code = :c")
                .param("c", code.name())
                .query(Long.class)
                .single();
    }

    private IndicatorSeries series(IndicatorCode code) {
        return repository.allSeries().stream()
                .filter(s -> s.code() == code)
                .findFirst()
                .orElseThrow();
    }

    private void seedPoint(IndicatorCode code) {
        repository.upsertPoints(code, List.of(valid(code)), NOW);
    }

    private LocalDate fromOf(IndicatorCode code) {
        return source.windows.stream()
                .filter(w -> w.code() == code)
                .findFirst()
                .orElseThrow()
                .from();
    }

    @Test
    void firstRunAsksThirteenMonths() {
        job.runOnce(false);

        assertThat(fromOf(IndicatorCode.CDI)).isEqualTo(LocalDate.parse("2025-10-24"));
        assertThat(source.windows).allSatisfy(w -> assertThat(w.to()).isEqualTo(LocalDate.parse("2026-11-24")));
    }

    @Test
    void dailyAsksThirtyDays() {
        seedPoint(IndicatorCode.CDI);

        job.runOnce(false);

        assertThat(fromOf(IndicatorCode.CDI)).isEqualTo(LocalDate.parse("2026-10-25"));
    }

    @Test
    void monthlyAsksTwentyFourMonths() {
        seedPoint(IndicatorCode.IPCA_MONTH);

        job.runOnce(false);

        assertThat(fromOf(IndicatorCode.IPCA_MONTH)).isEqualTo(LocalDate.parse("2024-11-01"));
    }

    @Test
    void successWritesPointsAndHealth() {
        IndicatorJob.RunReport report = job.runOnce(false);

        assertThat(report).isEqualTo(new IndicatorJob.RunReport(5, 0));
        for (IndicatorCode code : IndicatorCode.values()) {
            assertThat(pointsOf(code)).isEqualTo(1);
            assertThat(series(code).lastSuccessAt()).isEqualTo(NOW);
            assertThat(series(code).failureStreak()).isZero();
        }
    }

    @Test
    void outOfRangeWritesNothing(CapturedOutput output) {
        source.answers.put(
                IndicatorCode.SELIC_TARGET,
                c -> List.of(
                        valid(c),
                        new FetchedPoint(
                                LocalDate.parse("2026-11-23"), new BigDecimal("1500"), "https://example.test/x")));

        IndicatorJob.RunReport report = job.runOnce(false);

        assertThat(report).isEqualTo(new IndicatorJob.RunReport(4, 1));
        assertThat(pointsOf(IndicatorCode.SELIC_TARGET)).isZero();
        assertThat(series(IndicatorCode.SELIC_TARGET).failureStreak()).isEqualTo(1);
        assertThat(output.getAll())
                .contains("INDICATOR_ALERT type=FETCH_FAILED series=SELIC_TARGET")
                .contains("indicator fetch failed series=SELIC_TARGET kind=OUT_OF_RANGE")
                .doesNotContain("1500");
    }

    @Test
    void emptyIsAFailure(CapturedOutput output) {
        source.answers.put(IndicatorCode.IPCA_12M, c -> List.of());

        IndicatorJob.RunReport report = job.runOnce(false);

        assertThat(report).isEqualTo(new IndicatorJob.RunReport(4, 1));
        assertThat(series(IndicatorCode.IPCA_12M).failureStreak()).isEqualTo(1);
        assertThat(series(IndicatorCode.IPCA_12M).lastSuccessAt()).isNull();
        assertThat(output.getAll())
                .contains("INDICATOR_ALERT type=FETCH_FAILED series=IPCA_12M")
                .contains("kind=EMPTY");
    }

    @Test
    void oneFailureDoesNotStopTheOthers() {
        source.answers.put(IndicatorCode.CDI, c -> {
            throw new IndicatorFetchException(IndicatorFetchException.Kind.TIMEOUT);
        });

        IndicatorJob.RunReport report = job.runOnce(false);

        assertThat(report).isEqualTo(new IndicatorJob.RunReport(4, 1));
        assertThat(pointsOf(IndicatorCode.CDI)).isZero();
        for (IndicatorCode code : IndicatorCode.values()) {
            if (code != IndicatorCode.CDI) {
                assertThat(pointsOf(code)).isEqualTo(1);
            }
        }
        assertThat(series(IndicatorCode.CDI).failureStreak()).isEqualTo(1);
    }

    @Test
    void unexpectedFailureLogsOnlyTheClass(CapturedOutput output) {
        source.answers.put(IndicatorCode.CDI, c -> {
            throw new IllegalStateException("secret 9.99");
        });

        assertThat(job.runOnce(false)).isEqualTo(new IndicatorJob.RunReport(4, 1));
        assertThat(output.getAll())
                .contains("indicator fetch failed series=CDI kind=java.lang.IllegalStateException")
                .doesNotContain("secret 9.99");
    }

    @Test
    void staleAlertOnlyWhenAsked(CapturedOutput output) {
        // The collection fails for CDI, which last succeeded 5 weekdays before 2026-11-24 (a Tuesday): Nov 17.
        source.answers.put(IndicatorCode.CDI, c -> List.of());
        jdbc.sql("update indicator_series set last_success_at = :at where code = 'CDI'")
                .param("at", Timestamp.from(Instant.parse("2026-11-17T12:00:00Z")))
                .update();

        job.runOnce(false);
        assertThat(output.getAll()).doesNotContain("type=STALE");

        job.runOnce(true);
        assertThat(output.getAll()).contains("INDICATOR_ALERT type=STALE series=CDI");
        assertThat(count(output.getAll(), "type=STALE")).isEqualTo(1);
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    @Test
    void pausesBetweenSeries() {
        job.runOnce(false);

        assertThat(sleeper.waits).hasSize(4).containsOnly(Duration.ofSeconds(1));
    }

    @Test
    void secondRunWhileRunningIsSkipped() {
        List<IndicatorJob.RunReport> inner = new ArrayList<>();
        source.beforeAnswer = () -> {
            if (inner.isEmpty()) {
                inner.add(job.runOnce(false));
            }
        };

        IndicatorJob.RunReport outer = job.runOnce(false);

        assertThat(inner).containsExactly(new IndicatorJob.RunReport(0, 0));
        assertThat(outer).isEqualTo(new IndicatorJob.RunReport(5, 0));
        assertThat(source.windows).hasSize(5);
    }
}
