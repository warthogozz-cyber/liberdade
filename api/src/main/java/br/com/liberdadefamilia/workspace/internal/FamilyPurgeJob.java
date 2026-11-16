package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.FamilyDeletion;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Every night, deletes the families whose 30-day grace is over (D25). The foreign keys cascade to everything the family
 * owns, and the ones from the audit trail clear themselves. Each family goes in its own transaction, so one failure is
 * logged by id only and does not stop the others. The log carries only ids and the count.
 */
@Component
public class FamilyPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(FamilyPurgeJob.class);

    private final JdbcClient jdbc;
    private final Clock clock;
    private final TransactionTemplate tx;

    FamilyPurgeJob(JdbcClient jdbc, Clock clock, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    @Scheduled(cron = "0 50 3 * * *", zone = "America/Fortaleza")
    public void purge() {
        OffsetDateTime cutoff = OffsetDateTime.ofInstant(clock.instant().minus(FamilyDeletion.GRACE), ZoneOffset.UTC);
        List<UUID> due = jdbc.sql("select id from workspaces where deletion_requested_at < :cutoff order by id")
                .param("cutoff", cutoff)
                .query(UUID.class)
                .list();
        int purged = 0;
        for (UUID id : due) {
            try {
                // The cutoff is checked again, so a cancellation that landed after the list was read wins.
                Integer deleted = tx.execute(
                        status -> jdbc.sql("delete from workspaces where id = :id and deletion_requested_at < :cutoff")
                                .param("id", id)
                                .param("cutoff", cutoff)
                                .update());
                if (deleted != null && deleted == 1) {
                    purged++;
                }
            } catch (RuntimeException failure) {
                // The id only: the exception message could carry a value.
                log.error(
                        "Could not purge family {} ({})", id, failure.getClass().getName());
            }
        }
        log.info("Purged {} families past their deletion grace", purged);
    }
}
