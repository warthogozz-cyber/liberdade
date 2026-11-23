package br.com.liberdadefamilia.billing.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Every night, removes the webhook events older than 180 days, the paid orders older than five years and the orders
 * never paid that are older than 180 days; then reminds that refunds waiting for over 72 hours need a person. Rows go
 * in batches of 500, one transaction per batch. A failing step is logged by exception class only (the message could
 * carry a value) and does not stop the others. The clock is read once and every cutoff comes from it. The five years
 * are counted on the calendar in UTC.
 */
@Component
class BillingPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(BillingPurgeJob.class);

    private static final int BATCH = 500;
    private static final Duration EVENTS_KEEP = Duration.ofDays(180);
    private static final Duration UNPAID_KEEP = Duration.ofDays(180);
    private static final int PAID_KEEP_YEARS = 5;
    private static final Duration REFUND_STUCK_AFTER = Duration.ofHours(72);

    private final JdbcClient jdbc;
    private final BillingAlerts alerts;
    private final Clock clock;
    private final TransactionTemplate tx;

    BillingPurgeJob(JdbcClient jdbc, BillingAlerts alerts, Clock clock, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.alerts = alerts;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    @Scheduled(cron = "0 55 3 * * *", zone = "America/Fortaleza")
    public void run() {
        Instant now = clock.instant();
        Instant eventsCutoff = now.minus(EVENTS_KEEP);
        Instant paidCutoff =
                now.atZone(ZoneOffset.UTC).minusYears(PAID_KEEP_YEARS).toInstant();
        Instant unpaidCutoff = now.minus(UNPAID_KEEP);
        Instant stuckCutoff = now.minus(REFUND_STUCK_AFTER);

        int events = 0;
        int paid = 0;
        int unpaid = 0;
        try {
            events = deleteInBatches("""
                    delete from billing_webhook_events where event_id in (
                        select event_id from billing_webhook_events where received_at < :cutoff
                        order by received_at limit %d)""", eventsCutoff);
        } catch (RuntimeException failure) {
            log.warn(
                    "billing purge: events step failed ({})", failure.getClass().getName());
        }
        try {
            paid = deleteInBatches("""
                    delete from billing_orders where id in (
                        select id from billing_orders where paid_at < :cutoff
                        order by paid_at limit %d)""", paidCutoff);
        } catch (RuntimeException failure) {
            log.warn(
                    "billing purge: paid orders step failed ({})",
                    failure.getClass().getName());
        }
        try {
            unpaid = deleteInBatches("""
                    delete from billing_orders where id in (
                        select id from billing_orders where paid_at is null and created_at < :cutoff
                        order by created_at limit %d)""", unpaidCutoff);
        } catch (RuntimeException failure) {
            log.warn(
                    "billing purge: unpaid orders step failed ({})",
                    failure.getClass().getName());
        }
        log.info("Billing purge: {} events, {} paid orders, {} unpaid orders removed", events, paid, unpaid);

        remindStuckRefunds(stuckCutoff);
    }

    /** Runs the delete (which takes {@code %d} for the batch size) until a batch comes back short. */
    private int deleteInBatches(String sqlTemplate, Instant cutoff) {
        String sql = sqlTemplate.formatted(BATCH);
        int total = 0;
        int deleted;
        do {
            Integer count = tx.execute(status ->
                    jdbc.sql(sql).param("cutoff", Timestamp.from(cutoff)).update());
            deleted = count == null ? 0 : count;
            total += deleted;
        } while (deleted == BATCH);
        return total;
    }

    /** One alert per order stuck in REFUND_PENDING, with no id in the line; one failing alert does not stop the rest. */
    private void remindStuckRefunds(Instant cutoff) {
        long stuck;
        try {
            stuck = jdbc.sql("""
                            select count(*) from billing_orders
                             where status = 'REFUND_PENDING' and refund_requested_at < :cutoff""")
                    .param("cutoff", Timestamp.from(cutoff))
                    .query(Long.class)
                    .single();
        } catch (RuntimeException failure) {
            log.warn("billing purge: alert step failed ({})", failure.getClass().getName());
            return;
        }
        for (long i = 0; i < stuck; i++) {
            try {
                alerts.raise(AlertType.REFUND_STUCK);
            } catch (RuntimeException failure) {
                log.warn(
                        "billing purge: alert step failed ({})",
                        failure.getClass().getName());
            }
        }
    }
}
