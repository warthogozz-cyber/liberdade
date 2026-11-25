package br.com.liberdadefamilia.gamification.internal;

import br.com.liberdadefamilia.ledger.AccountChanged;
import br.com.liberdadefamilia.ledger.TransactionChanged;
import br.com.liberdadefamilia.planning.GoalChanged;
import br.com.liberdadefamilia.planning.ReserveCostChanged;
import br.com.liberdadefamilia.planning.ReserveTargetChanged;
import br.com.liberdadefamilia.ventures.VentureActivated;
import br.com.liberdadefamilia.workspace.MemberJoined;
import br.com.liberdadefamilia.workspace.MemberRemoved;
import br.com.liberdadefamilia.workspace.MemberRoleChanged;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.ModuleToggled;
import br.com.liberdadefamilia.workspace.Modules;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Grants the achievements once what earns them has committed (D6). Each evaluation runs after the commit, in a
 * transaction of its own, so a failure is logged by its class only and never undoes or fails the action of the person.
 * The achievements of a membership that left are erased in the transaction that ended it.
 */
@Component
class AchievementListeners {

    private static final Logger log = LoggerFactory.getLogger(AchievementListeners.class);

    private final AchievementService service;
    private final AchievementRepository repository;
    private final Modules modules;
    private final TransactionTemplate own;

    AchievementListeners(
            AchievementService service,
            AchievementRepository repository,
            Modules modules,
            PlatformTransactionManager transactionManager) {
        this.service = service;
        this.repository = repository;
        this.modules = modules;
        this.own = new TransactionTemplate(transactionManager);
        this.own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(TransactionChanged event) {
        family(event.workspaceId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(AccountChanged event) {
        family(event.workspaceId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(GoalChanged event) {
        family(event.workspaceId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ReserveCostChanged event) {
        family(event.workspaceId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ReserveTargetChanged event) {
        family(event.workspaceId());
    }

    /** Starting a project is the action of whoever created it, so it is celebrated; a creator who left gets nothing. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(VentureActivated event) {
        UUID creator = event.createdByMembershipId();
        if (creator != null) {
            run(() -> service.evaluate(event.workspaceId(), creator, false));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ModuleToggled event) {
        boolean gamification = event.key() == ModuleKey.GAMIFICATION;
        boolean planning = event.key() == ModuleKey.PLANNING;
        if (!event.enabled() || !gamification && !planning) {
            return;
        }
        run(() -> {
            if (gamification || modules.isEnabled(event.workspaceId(), ModuleKey.GAMIFICATION)) {
                service.evaluateFamily(event.workspaceId(), true);
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(MemberJoined event) {
        run(() -> service.evaluate(event.workspaceId(), event.membershipId(), true));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(MemberRoleChanged event) {
        run(() -> service.evaluate(event.workspaceId(), event.membershipId(), true));
    }

    /** Runs inside the transaction that ended the membership, whatever the reason. */
    @EventListener
    void on(MemberRemoved event) {
        repository.deleteFor(event.workspaceId(), event.membershipId());
    }

    private void family(UUID workspaceId) {
        run(() -> service.evaluateFamily(workspaceId, false));
    }

    private void run(Runnable evaluation) {
        try {
            own.executeWithoutResult(status -> evaluation.run());
        } catch (RuntimeException failure) {
            log.warn("achievement evaluation failed ({})", failure.getClass().getName());
        }
    }
}
