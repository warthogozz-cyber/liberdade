package br.com.liberdadefamilia.privacy.internal;

import br.com.liberdadefamilia.audit.AuditEntry;
import br.com.liberdadefamilia.audit.AuditTrail;
import br.com.liberdadefamilia.workspace.MemberJoined;
import br.com.liberdadefamilia.workspace.MemberRoleChanged;
import br.com.liberdadefamilia.workspace.MemberSummary;
import br.com.liberdadefamilia.workspace.Memberships;
import br.com.liberdadefamilia.workspace.Role;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Records the parental consent when a YOUTH comes in, by invitation or by a change of role. Plain and synchronous, so
 * it joins the publisher's transaction: the consent and the member stand or fall together.
 */
@Component
class ConsentListener {

    static final String PARENTAL_DOCUMENT_VERSION = "parental-2026-11";
    static final Map<String, String> PARENTAL_METADATA = Map.of("purpose", ConsentPurpose.PARENTAL.name());

    private final ConsentRepository consents;
    private final Memberships memberships;
    private final AuditTrail trail;

    ConsentListener(ConsentRepository consents, Memberships memberships, AuditTrail trail) {
        this.consents = consents;
        this.memberships = memberships;
        this.trail = trail;
    }

    @EventListener
    void on(MemberJoined event) {
        if (event.role() != Role.YOUTH) {
            return;
        }
        if (event.parentalConsentBy() == null) {
            throw new IllegalStateException("A YOUTH joined without a guardian's consent");
        }
        grant(event.workspaceId(), event.membershipId(), event.userId(), event.parentalConsentBy());
    }

    @EventListener
    void on(MemberRoleChanged event) {
        if (event.to() != Role.YOUTH) {
            return;
        }
        // The event names the membership, not the person.
        MemberSummary member = memberships
                .find(event.workspaceId(), event.membershipId())
                .orElseThrow(() -> new IllegalStateException("The member whose role changed is gone"));
        if (member.userId() == null) {
            throw new IllegalStateException("The member whose role changed has no account");
        }
        grant(event.workspaceId(), event.membershipId(), member.userId(), event.actorUserId());
    }

    private void grant(UUID workspaceId, UUID membershipId, UUID userId, UUID grantedBy) {
        consents.supersedeOpen(userId, workspaceId, ConsentPurpose.PARENTAL);
        consents.insert(userId, workspaceId, ConsentPurpose.PARENTAL, PARENTAL_DOCUMENT_VERSION, grantedBy);
        trail.record(new AuditEntry(
                workspaceId, grantedBy, "consent.granted", "MEMBERSHIP", membershipId, PARENTAL_METADATA));
    }
}
