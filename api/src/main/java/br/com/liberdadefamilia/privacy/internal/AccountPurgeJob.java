package br.com.liberdadefamilia.privacy.internal;

import br.com.liberdadefamilia.identity.UserDeletion;
import br.com.liberdadefamilia.workspace.Memberships;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Every night, erases the accounts whose 30-day grace is over (D24). Each person goes in their own transaction: first
 * the memberships let go of them (the family keeps its transactions, under the nickname), then their identity,
 * passkeys and sessions go. One failure is logged by id only and does not stop the others.
 */
@Component
public class AccountPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeJob.class);

    private final UserDeletion deletion;
    private final Memberships memberships;
    private final Clock clock;
    private final TransactionTemplate tx;

    AccountPurgeJob(
            UserDeletion deletion, Memberships memberships, Clock clock, PlatformTransactionManager transactions) {
        this.deletion = deletion;
        this.memberships = memberships;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    @Scheduled(cron = "0 45 3 * * *", zone = "America/Fortaleza")
    public void purge() {
        int purged = 0;
        for (UUID id : deletion.dueForPurge(clock.instant())) {
            try {
                tx.executeWithoutResult(status -> {
                    memberships.detachUser(id);
                    deletion.purge(id);
                });
                purged++;
            } catch (RuntimeException failure) {
                // The id only: the exception message could carry a value.
                log.error("Could not purge account {}", id);
            }
        }
        log.info("Purged {} accounts past their deletion grace", purged);
    }
}
