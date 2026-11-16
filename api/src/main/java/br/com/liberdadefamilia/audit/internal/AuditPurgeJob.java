package br.com.liberdadefamilia.audit.internal;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every night, removes the audit events older than 12 months (LGPD retention). The log carries only the count. */
@Component
public class AuditPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(AuditPurgeJob.class);

    private final AuditRepository repository;
    private final Clock clock;

    AuditPurgeJob(AuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 10 3 * * *", zone = "America/Fortaleza")
    public void purge() {
        OffsetDateTime cutoff =
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusMonths(JdbcAuditTrail.KEEP_MONTHS);
        int purged = repository.purgeBefore(cutoff);
        log.info("Purged {} audit events older than {} months", purged, JdbcAuditTrail.KEEP_MONTHS);
    }
}
