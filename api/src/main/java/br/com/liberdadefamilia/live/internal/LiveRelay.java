package br.com.liberdadefamilia.live.internal;

import br.com.liberdadefamilia.gamification.AchievementsEarned;
import br.com.liberdadefamilia.ledger.AccountChanged;
import br.com.liberdadefamilia.ledger.AccountOwnerChanged;
import br.com.liberdadefamilia.ledger.CategoryChanged;
import br.com.liberdadefamilia.ledger.TagChanged;
import br.com.liberdadefamilia.ledger.TransactionChanged;
import br.com.liberdadefamilia.live.internal.Audience.AccountViewers;
import br.com.liberdadefamilia.live.internal.Audience.Everyone;
import br.com.liberdadefamilia.live.internal.Audience.Member;
import br.com.liberdadefamilia.live.internal.Audience.NoYouth;
import br.com.liberdadefamilia.markets.ProductsChanged;
import br.com.liberdadefamilia.markets.ProductsMerged;
import br.com.liberdadefamilia.markets.ShoppingListChanged;
import br.com.liberdadefamilia.planning.BudgetChanged;
import br.com.liberdadefamilia.planning.GoalArchived;
import br.com.liberdadefamilia.planning.GoalChanged;
import br.com.liberdadefamilia.planning.RecurringRuleArchived;
import br.com.liberdadefamilia.planning.RecurringRuleCreated;
import br.com.liberdadefamilia.planning.RecurringRuleEdited;
import br.com.liberdadefamilia.planning.ReserveCostChanged;
import br.com.liberdadefamilia.planning.ReserveTargetChanged;
import br.com.liberdadefamilia.ventures.VentureChanged;
import br.com.liberdadefamilia.ventures.VentureClosed;
import br.com.liberdadefamilia.ventures.VentureDeleted;
import br.com.liberdadefamilia.workspace.MemberJoined;
import br.com.liberdadefamilia.workspace.MemberProfileChanged;
import br.com.liberdadefamilia.workspace.MemberRemoved;
import br.com.liberdadefamilia.workspace.MemberRoleChanged;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.ModuleToggled;
import br.com.liberdadefamilia.workspace.Modules;
import br.com.liberdadefamilia.workspace.WorkspaceDeletionRequested;
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
 * <p>The planning events go out only while the family has Planning on, the achievement notice only while it has
 * Conquistas on, the shopping-list notice only while it has Feira on; the check runs once per event, not per stream.
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
                null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(AccountChanged event) {
        send(
                event.workspaceId(),
                "account",
                event.accountId(),
                new AccountViewers(event.accountId(), null, Set.of()),
                null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(AccountOwnerChanged event) {
        UUID former = event.formerOwnerMembershipId();
        send(
                event.workspaceId(),
                "account",
                event.accountId(),
                new AccountViewers(event.accountId(), null, former == null ? Set.of() : Set.of(former)),
                null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(CategoryChanged event) {
        send(event.workspaceId(), "category", event.categoryId(), new Everyone(), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(TagChanged event) {
        send(event.workspaceId(), "tag", event.tagId(), new Everyone(), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(RecurringRuleCreated event) {
        send(event.workspaceId(), "rule", event.ruleId(), new NoYouth(), ModuleKey.PLANNING);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(RecurringRuleEdited event) {
        send(event.workspaceId(), "rule", event.ruleId(), new NoYouth(), ModuleKey.PLANNING);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(RecurringRuleArchived event) {
        send(event.workspaceId(), "rule", event.ruleId(), new NoYouth(), ModuleKey.PLANNING);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(BudgetChanged event) {
        send(event.workspaceId(), "budget", null, new NoYouth(), ModuleKey.PLANNING);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GoalChanged event) {
        send(event.workspaceId(), "goal", event.goalId(), new Everyone(), ModuleKey.PLANNING);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GoalArchived event) {
        send(event.workspaceId(), "goal", event.goalId(), new Everyone(), ModuleKey.PLANNING);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ReserveTargetChanged event) {
        send(event.workspaceId(), "reserve", null, new NoYouth(), ModuleKey.PLANNING);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ReserveCostChanged event) {
        send(event.workspaceId(), "reserve", null, new NoYouth(), ModuleKey.PLANNING);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(VentureChanged event) {
        send(event.workspaceId(), "venture", event.ventureId(), new NoYouth(), ModuleKey.VENTURES);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(VentureClosed event) {
        send(event.workspaceId(), "venture", event.ventureId(), new NoYouth(), ModuleKey.VENTURES);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(VentureDeleted event) {
        send(event.workspaceId(), "venture", event.ventureId(), new NoYouth(), ModuleKey.VENTURES);
    }

    /** The list, the comparison and the products screens all refetch on this one notice; it names no item (D10). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ShoppingListChanged event) {
        send(event.workspaceId(), "shopping-list", null, new Everyone(), ModuleKey.MARKETS);
    }

    /** A rename or a new unit changes what the list shows, so it tells the list. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ProductsChanged event) {
        send(event.workspaceId(), "shopping-list", null, new Everyone(), ModuleKey.MARKETS);
    }

    /** A join moves the prices and may fold two list items into one. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ProductsMerged event) {
        send(event.workspaceId(), "shopping-list", null, new Everyone(), ModuleKey.MARKETS);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(MemberJoined event) {
        send(event.workspaceId(), "members", null, new Everyone(), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(MemberRemoved event) {
        closeMember(event.workspaceId(), event.membershipId());
        send(event.workspaceId(), "members", null, new Everyone(), null);
    }

    /** The streams of the person close, so they reconnect with the new role (or are turned away if gone). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(MemberRoleChanged event) {
        closeMember(event.workspaceId(), event.membershipId());
        send(event.workspaceId(), "members", null, new Everyone(), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(WorkspaceDeletionRequested event) {
        try {
            streams.closeWorkspace(event.workspaceId());
        } catch (RuntimeException failure) {
            log.warn("live close failed ({})", failure.getClass().getName());
        }
    }

    private void closeMember(UUID workspaceId, UUID membershipId) {
        try {
            streams.closeMember(workspaceId, membershipId);
        } catch (RuntimeException failure) {
            log.warn("live close failed ({})", failure.getClass().getName());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(MemberProfileChanged event) {
        send(event.workspaceId(), "members", null, new Everyone(), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ModuleToggled event) {
        send(event.workspaceId(), "modules", null, new Everyone(), null);
    }

    /** Only the person who earned it hears about it, and only while Conquistas is on (D10). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(AchievementsEarned event) {
        send(event.workspaceId(), "achievement", null, new Member(event.membershipId()), ModuleKey.GAMIFICATION);
    }

    private void send(
            UUID workspaceId, String entity, @Nullable UUID id, Audience audience, @Nullable ModuleKey module) {
        try {
            if (module != null && !modules.isEnabled(workspaceId, module)) {
                return;
            }
            streams.publish(workspaceId, new LiveChange(entity, id), audience);
        } catch (RuntimeException failure) {
            log.warn("live notice failed ({})", failure.getClass().getName());
        }
    }
}
