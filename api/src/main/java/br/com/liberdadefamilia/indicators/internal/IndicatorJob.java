package br.com.liberdadefamilia.indicators.internal;

import br.com.liberdadefamilia.indicators.domain.Staleness;
import br.com.liberdadefamilia.indicators.domain.ValueRange;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Collects the five series twice a day (D4). Each series stands alone: a failure marks that series and the others go
 * on. The logs carry the series code and the kind of failure, never a value and never a URL.
 */
@Component
public class IndicatorJob {

    /** What one collection did. */
    public record RunReport(int ok, int failed) {}

    private static final Logger log = LoggerFactory.getLogger(IndicatorJob.class);
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final int DAILY_DAYS = 30;
    private static final int MONTHLY_MONTHS = 24;
    private static final int FIRST_RUN_MONTHS = 13;

    private final IndicatorRepository repository;
    private final IndicatorSource source;
    private final IndicatorAlerts alerts;
    private final Sleeper sleeper;
    private final IndicatorProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final AtomicBoolean running = new AtomicBoolean();

    IndicatorJob(
            IndicatorRepository repository,
            IndicatorSource source,
            IndicatorAlerts alerts,
            Sleeper sleeper,
            IndicatorProperties properties,
            Clock clock,
            PlatformTransactionManager transactions) {
        this.repository = repository;
        this.source = source;
        this.alerts = alerts;
        this.sleeper = sleeper;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    @Scheduled(cron = "0 30 8,19 * * *", zone = "America/Sao_Paulo")
    public void run() {
        runOnce(clock.instant().atZone(ZONE).getHour() < 12);
    }

    /** One collection of all series. A call while another is running returns at once, without fetching. */
    public RunReport runOnce(boolean checkStale) {
        if (!running.compareAndSet(false, true)) {
            return new RunReport(0, 0);
        }
        try {
            return collect(checkStale);
        } finally {
            running.set(false);
        }
    }

    private RunReport collect(boolean checkStale) {
        int ok = 0;
        int failed = 0;
        List<IndicatorSeries> all = repository.allSeries();
        for (int i = 0; i < all.size(); i++) {
            if (i > 0 && !pause()) {
                alerts.run(ok, failed);
                return new RunReport(ok, failed);
            }
            if (collectOne(all.get(i))) {
                ok++;
            } else {
                failed++;
            }
        }
        if (checkStale) {
            Instant now = clock.instant();
            for (IndicatorSeries series : repository.allSeries()) {
                if (Staleness.isStale(series.lastSuccessAt(), now, ZONE)) {
                    alerts.stale(series.code());
                }
            }
        }
        alerts.run(ok, failed);
        return new RunReport(ok, failed);
    }

    /** False when the thread was interrupted while waiting; the interruption is given back to the thread. */
    private boolean pause() {
        try {
            sleeper.sleep(properties.pauseBetweenSeries());
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean collectOne(IndicatorSeries series) {
        IndicatorCode code = series.code();
        try {
            LocalDate today = clock.instant().atZone(ZONE).toLocalDate();
            List<FetchedPoint> points = source.fetch(code, series.sourceRef(), windowStart(code, today), today);
            if (points.isEmpty()) {
                throw new IndicatorFetchException(IndicatorFetchException.Kind.EMPTY);
            }
            if (points.stream().anyMatch(p -> !ValueRange.accepts(series.unit(), p.value()))) {
                throw new IndicatorFetchException(IndicatorFetchException.Kind.OUT_OF_RANGE);
            }
            Instant at = clock.instant();
            tx.executeWithoutResult(status -> {
                repository.upsertPoints(code, points, at);
                repository.markSuccess(code, at);
            });
            return true;
        } catch (IndicatorFetchException failure) {
            failed(code, failure.kind().name());
        } catch (RuntimeException failure) {
            failed(code, failure.getClass().getName());
        }
        return false;
    }

    private void failed(IndicatorCode code, String kind) {
        repository.markFailure(code, clock.instant());
        alerts.fetchFailed(code);
        log.warn("indicator fetch failed series={} kind={}", code, kind);
    }

    private LocalDate windowStart(IndicatorCode code, LocalDate today) {
        if (repository.lastRefDate(code).isEmpty()) {
            return today.minusMonths(FIRST_RUN_MONTHS);
        }
        return code.frequency() == IndicatorCode.Frequency.DAILY
                ? today.minusDays(DAILY_DAYS)
                : today.minusMonths(MONTHLY_MONTHS).withDayOfMonth(1);
    }
}
