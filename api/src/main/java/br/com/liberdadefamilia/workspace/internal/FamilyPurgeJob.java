package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.FamilyDeletion;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every night, deletes the families whose 30-day grace is over (D25). The foreign keys cascade to everything the family
 * owns, and the ones from the audit trail clear themselves. The log carries only the count.
 */
@Component
public class FamilyPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(FamilyPurgeJob.class);

    private final JdbcClient jdbc;
    private final Clock clock;

    FamilyPurgeJob(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "0 50 3 * * *", zone = "America/Fortaleza")
    @Transactional
    public void purge() {
        OffsetDateTime cutoff = OffsetDateTime.ofInstant(clock.instant().minus(FamilyDeletion.GRACE), ZoneOffset.UTC);
        int purged = jdbc.sql("delete from workspaces where deletion_requested_at < :cutoff")
                .param("cutoff", cutoff)
                .update();
        log.info("Purged {} families past their deletion grace", purged);
    }
}
