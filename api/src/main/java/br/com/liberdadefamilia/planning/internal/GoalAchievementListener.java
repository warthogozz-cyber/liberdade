package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.ledger.AccountChanged;
import br.com.liberdadefamilia.ledger.TransactionChanged;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Marks reached the goals that follow an account once the ledger moved it (D4, D9): after the ledger's transaction
 * commits, in a transaction of its own. A failure never undoes or fails the ledger's change; it is logged by its class
 * only, and the next read of the goals does the check again.
 *
 * <p>The listener is idempotent: the {@code UPDATE} only writes where {@code achieved_at IS NULL}, so a repeated event
 * marks nothing and publishes nothing. The check on read covers a listener that failed.
 */
@Component
class GoalAchievementListener {

    private static final Logger log = LoggerFactory.getLogger(GoalAchievementListener.class);

    private final GoalService goals;

    GoalAchievementListener(GoalService goals) {
        this.goals = goals;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(TransactionChanged event) {
        Set<UUID> accounts = new LinkedHashSet<>();
        accounts.add(event.accountId());
        if (event.toAccountId() != null) {
            accounts.add(event.toAccountId());
        }
        check(event.workspaceId(), accounts);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(AccountChanged event) {
        check(event.workspaceId(), Set.of(event.accountId()));
    }

    private void check(UUID workspaceId, Set<UUID> accounts) {
        try {
            goals.reachLinked(workspaceId, accounts);
        } catch (RuntimeException failure) {
            log.warn("goal achievement check failed ({})", failure.getClass().getName());
        }
    }
}
