package br.com.liberdadefamilia.ledger.internal;

import static java.time.temporal.ChronoUnit.DAYS;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every night, removes for good the transactions deleted more than 30 days ago (D20). Until then a deleted transaction
 * can be restored. A transaction a recurring rule made also waits until the second month after its date, so that the
 * generator does not make it again. The log carries only how many were removed.
 */
@Component
public class TransactionPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(TransactionPurgeJob.class);
    private static final int KEEP_DAYS = 30;

    private final TransactionRepository transactions;
    private final Clock clock;

    TransactionPurgeJob(TransactionRepository transactions, Clock clock) {
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "America/Fortaleza")
    public void purge() {
        // A row a recurring rule made also waits until its month is over by a month (the first of the previous month).
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        int purged = transactions.purgeDeletedBefore(
                clock.instant().minus(KEEP_DAYS, DAYS), today.withDayOfMonth(1).minusMonths(1));
        log.info("Purged {} transactions deleted over {} days ago", purged, KEEP_DAYS);
    }
}
