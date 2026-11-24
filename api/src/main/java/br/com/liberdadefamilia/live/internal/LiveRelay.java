package br.com.liberdadefamilia.live.internal;

import br.com.liberdadefamilia.ledger.AccountChanged;
import br.com.liberdadefamilia.ledger.AccountOwnerChanged;
import br.com.liberdadefamilia.ledger.CategoryChanged;
import br.com.liberdadefamilia.ledger.TagChanged;
import br.com.liberdadefamilia.ledger.TransactionChanged;
import br.com.liberdadefamilia.live.internal.Audience.AccountViewers;
import br.com.liberdadefamilia.live.internal.Audience.Everyone;
import br.com.liberdadefamilia.live.internal.Audience.NoYouth;
import br.com.liberdadefamilia.planning.BudgetChanged;
import br.com.liberdadefamilia.planning.GoalArchived;
import br.com.liberdadefamilia.planning.GoalChanged;
import br.com.liberdadefamilia.planning.RecurringRuleArchived;
import br.com.liberdadefamilia.planning.RecurringRuleCreated;
import br.com.liberdadefamilia.planning.RecurringRuleEdited;
import br.com.liberdadefamilia.planning.ReserveCostChanged;
import br.com.liberdadefamilia.planning.ReserveTargetChanged;
import br.com.liberdadefamilia.workspace.MemberJoined;
import br.com.liberdadefamilia.workspace.MemberProfileChanged;
import br.com.liberdadefamilia.workspace.MemberRemoved;
import br.com.liberdadefamilia.workspace.MemberRoleChanged;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.ModuleToggled;
import br.com.liberdadefamilia.workspace.Modules;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns the domain events into notices once the change is committed (D3): a rolled-back write says nothing. It opens
 * no transaction; it only reads the family's modules and hands the notice over, and a failure never reaches the code
 * that published the event. It is logged by its class only.
 *
 * <p>The planning events go out only while the family has Planning on; the check runs once per event, not per stream.
 */
@Component
class LiveRelay {

    private static final Logger log = LoggerFactory.getLogger(LiveRelay.class);

    private final LiveStreams streams;
    private final Modules modules;

    LiveRelay(LiveStreams streams, Modules modules) {
        this.streams = streams;
        this.modules = modules;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(TransactionChanged event) {
        send(
                event.workspaceId(),
                "transaction",
                event.transactionId(),
                new AccountViewers(event.accountId(), event.toAccountId(), Set.of()),
                false);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(AccountChanged event) {
        send(
                event.workspaceId(),
                "account",
                event.accountId(),
                new AccountViewers(event.accountId(), null, Set.of()),
                false);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(AccountOwnerChanged event) {
        UUID former = event.formerOwnerMembershipId();
        send(
                event.workspaceId(),
                "account",
                event.accountId(),
                new AccountViewers(event.accountId(), null, former == null ? Set.of() : Set.of(former)),
                false);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(CategoryChanged event) {
        send(event.workspaceId(), "category", event.categoryId(), new Everyone(), false);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(TagChanged event) {
        send(event.workspaceId(), "tag", event.tagId(), new Everyone(), false);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(RecurringRuleCreated event) {
        send(event.workspaceId(), "rule", event.ruleId(), new NoYouth(), true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(RecurringRuleEdited event) {
        send(event.workspaceId(), "rule", event.ruleId(), new NoYouth(), true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(RecurringRuleArchived event) {
        send(event.workspaceId(), "rule", event.ruleId(), new NoYouth(), true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(BudgetChanged event) {
        send(event.workspaceId(), "budget", null, new NoYouth(), true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GoalChanged event) {
        send(event.workspaceId(), "goal", event.goalId(), new Everyone(), true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GoalArchived event) {
        send(event.workspaceId(), "goal", event.goalId(), new Everyone(), true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ReserveTargetChanged event) {
        send(event.workspaceId(), "reserve", null, new NoYouth(), true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ReserveCostChanged event) {
        send(event.workspaceId(), "reserve", null, new NoYouth(), true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(MemberJoined event) {
        send(event.workspaceId(), "members", null, new Everyone(), false);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(MemberRemoved event) {
        send(event.workspaceId(), "members", null, new Everyone(), false);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(MemberRoleChanged event) {
        send(event.workspaceId(), "members", null, new Everyone(), false);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(MemberProfileChanged event) {
        send(event.workspaceId(), "members", null, new Everyone(), false);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ModuleToggled event) {
        send(event.workspaceId(), "modules", null, new Everyone(), false);
    }

    private void send(UUID workspaceId, String entity, @Nullable UUID id, Audience audience, boolean needsPlanning) {
        try {
            if (needsPlanning && !modules.isEnabled(workspaceId, ModuleKey.PLANNING)) {
                return;
            }
            streams.publish(workspaceId, new LiveChange(entity, id), audience);
        } catch (RuntimeException failure) {
            log.warn("live notice failed ({})", failure.getClass().getName());
        }
    }
}
