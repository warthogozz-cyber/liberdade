package br.com.liberdadefamilia.indicators.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.liberdadefamilia.indicators.domain.IndicatorUnit;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;

/** The job with stand-ins for the database and the source: no container, no background threads left behind. */
@ExtendWith(OutputCaptureExtension.class)
class IndicatorJobTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    private final IndicatorRepository repository = mock(IndicatorRepository.class);
    private final IndicatorSeries series = new IndicatorSeries(
            IndicatorCode.SELIC_TARGET, "BCB", "432", IndicatorUnit.PERCENT_YEAR, "Selic", null, null, 0);

    private IndicatorJob job(IndicatorSource source) {
        when(repository.allSeries()).thenReturn(List.of(series));
        when(repository.lastRefDate(any())).thenReturn(Optional.empty());
        return new IndicatorJob(
                repository,
                source,
                new IndicatorAlerts(),
                duration -> {},
                new IndicatorProperties(null, null, null, null, null, null, Duration.ZERO),
                Clock.fixed(NOW, ZoneOffset.UTC),
                mock(PlatformTransactionManager.class));
    }

    private static FetchedPoint point() {
        return new FetchedPoint(LocalDate.parse("2026-11-23"), new BigDecimal("15.00"), "https://example.test/x");
    }

    @Test
    void alertIsLoggedEvenWhenRecordingTheFailureFails(CapturedOutput output) {
        IndicatorJob job = job((code, ref, from, to) -> List.of());
        doThrow(new IllegalStateException("db down")).when(repository).markFailure(any(), any());

        assertThatThrownBy(() -> job.runOnce(false)).isInstanceOf(IllegalStateException.class);

        assertThat(output.getAll())
                .contains("INDICATOR_ALERT type=FETCH_FAILED series=SELIC_TARGET")
                .contains("indicator fetch failed series=SELIC_TARGET kind=EMPTY")
                // A crashed run writes no INDICATOR_RUN line: that is what the run-missing alarm catches.
                .doesNotContain("INDICATOR_RUN");
    }

    @Test
    void scheduledEntryReturnsWithoutWaitingForTheWork(CapturedOutput output) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        IndicatorJob job = job((code, ref, from, to) -> {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return List.of(point());
        });

        job.run();

        // The fetch is parked on the latch, so the work is running elsewhere while run() has already returned.
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        // The guard still holds: a second trigger while one runs is skipped.
        assertThat(job.runOnce(false)).isEqualTo(new IndicatorJob.RunReport(0, 0));
        release.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!output.getAll().contains("INDICATOR_RUN ok=1 failed=0") && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(output.getAll()).contains("INDICATOR_RUN ok=1 failed=0");
    }

    @Test
    void aCrashOnTheScheduledThreadIsLoggedByClassOnly(CapturedOutput output) throws Exception {
        IndicatorJob job = job((code, ref, from, to) -> List.of());
        doThrow(new IllegalStateException("secret-value-1500.123456"))
                .when(repository)
                .markFailure(any(), any());

        job.run();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!output.getAll().contains("indicator run crashed") && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(output.getAll())
                .contains("indicator run crashed kind=java.lang.IllegalStateException")
                .doesNotContain("secret-value")
                .doesNotContain("INDICATOR_RUN");
    }
}
