package br.com.liberdadefamilia.audit.internal;

import br.com.liberdadefamilia.audit.AuditEntry;
import br.com.liberdadefamilia.audit.AuditTrail;
import br.com.liberdadefamilia.identity.SignedIn;
import br.com.liberdadefamilia.workspace.InvitationCreated;
import br.com.liberdadefamilia.workspace.InvitationKind;
import br.com.liberdadefamilia.workspace.InvitationRevoked;
import br.com.liberdadefamilia.workspace.MemberJoined;
import br.com.liberdadefamilia.workspace.MemberRemoved;
import br.com.liberdadefamilia.workspace.MemberRoleChanged;
import br.com.liberdadefamilia.workspace.ModuleToggled;
import br.com.liberdadefamilia.workspace.RecoveryCreated;
import br.com.liberdadefamilia.workspace.RecoveryUsed;
import br.com.liberdadefamilia.workspace.WorkspaceDeletionCancelled;
import br.com.liberdadefamilia.workspace.WorkspaceDeletionRequested;
import br.com.liberdadefamilia.workspace.WorkspaceRenamed;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * One plain, synchronous listener per event. They join the publisher's transaction, so an action that fails takes its
 * audit row with it; {@code SignedIn} is published after commit and its row simply commits on its own. Metadata holds
 * fixed labels only: no names, no addresses.
 */
@Component
class AuditListeners {

    private final AuditTrail trail;

    AuditListeners(AuditTrail trail) {
        this.trail = trail;
    }

    @EventListener
    void on(SignedIn event) {
        record(
                null,
                event.userId(),
                "auth.signed_in",
                "USER",
                event.userId(),
                Map.of(
                        "method", event.method().name(),
                        "browser", event.device().browser(),
                        "os", event.device().os()));
    }

    @EventListener
    void on(MemberJoined event) {
        record(
                event.workspaceId(),
                event.userId(),
                "member.joined",
                "MEMBERSHIP",
                event.membershipId(),
                Map.of("role", event.role().name()));
    }

    @EventListener
    void on(MemberRoleChanged event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                "member.role_changed",
                "MEMBERSHIP",
                event.membershipId(),
                Map.of("from", event.from().name(), "to", event.to().name()));
    }

    @EventListener
    void on(MemberRemoved event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                "member.removed",
                "MEMBERSHIP",
                event.membershipId(),
                Map.of("reason", event.reason().name()));
    }

    @EventListener
    void on(WorkspaceRenamed event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                "workspace.renamed",
                "WORKSPACE",
                event.workspaceId(),
                Map.of());
    }

    @EventListener
    void on(WorkspaceDeletionRequested event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                "workspace.deletion_requested",
                "WORKSPACE",
                event.workspaceId(),
                Map.of());
    }

    @EventListener
    void on(WorkspaceDeletionCancelled event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                "workspace.deletion_cancelled",
                "WORKSPACE",
                event.workspaceId(),
                Map.of());
    }

    @EventListener
    void on(InvitationCreated event) {
        if (event.kind() != InvitationKind.INVITE) {
            return;
        }
        record(
                event.workspaceId(),
                event.actorUserId(),
                "invitation.created",
                "INVITATION",
                event.invitationId(),
                Map.of("role", event.role().name()));
    }

    @EventListener
    void on(InvitationRevoked event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                "invitation.revoked",
                "INVITATION",
                event.invitationId(),
                Map.of());
    }

    @EventListener
    void on(RecoveryCreated event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                "recovery.created",
                "MEMBERSHIP",
                event.targetMembershipId(),
                Map.of());
    }

    @EventListener
    void on(RecoveryUsed event) {
        record(
                event.workspaceId(),
                event.userId(),
                "recovery.used",
                "MEMBERSHIP",
                event.targetMembershipId(),
                Map.of());
    }

    @EventListener
    void on(ModuleToggled event) {
        record(
                event.workspaceId(),
                event.actorUserId(),
                "module.toggled",
                "MODULE",
                null,
                Map.of("key", event.key().wire(), "enabled", String.valueOf(event.enabled())));
    }

    private void record(
            @Nullable UUID workspaceId,
            @Nullable UUID actorUserId,
            String action,
            String targetType,
            @Nullable UUID targetId,
            Map<String, String> metadata) {
        trail.record(new AuditEntry(workspaceId, actorUserId, action, targetType, targetId, metadata));
    }
}
