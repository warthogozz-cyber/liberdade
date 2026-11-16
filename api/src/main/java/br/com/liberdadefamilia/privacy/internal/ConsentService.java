package br.com.liberdadefamilia.privacy.internal;

import br.com.liberdadefamilia.audit.AuditEntry;
import br.com.liberdadefamilia.audit.AuditTrail;
import br.com.liberdadefamilia.workspace.MemberRemoved;
import br.com.liberdadefamilia.workspace.MemberSummary;
import br.com.liberdadefamilia.workspace.MembershipStatus;
import br.com.liberdadefamilia.workspace.Memberships;
import br.com.liberdadefamilia.workspace.Role;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists the consents that involve a person and lets the guardian who gave one take it back. */
@Service
class ConsentService {

    private static final String NOT_FOUND_DETAIL = "Consentimento não encontrado.";
    private static final String FORBIDDEN_DETAIL = "Só quem deu o consentimento pode revogá-lo.";

    private final ConsentRepository consents;
    private final Memberships memberships;
    private final AuditTrail trail;

    ConsentService(ConsentRepository consents, Memberships memberships, AuditTrail trail) {
        this.consents = consents;
        this.memberships = memberships;
        this.trail = trail;
    }

    @Transactional(readOnly = true)
    List<ConsentView> list(UUID userId) {
        List<ConsentRepository.Row> rows = consents.involving(userId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<UUID> workspaceIds = new HashSet<>();
        Set<UUID> userIds = new HashSet<>();
        for (ConsentRepository.Row row : rows) {
            workspaceIds.add(row.workspaceId());
            userIds.add(row.userId());
            if (row.grantedBy() != null) {
                userIds.add(row.grantedBy());
            }
        }
        Map<UUID, String> workspaceNames = memberships.workspaceNames(workspaceIds);
        Map<String, MemberSummary> members = new HashMap<>();
        for (MemberSummary member : memberships.membersOf(workspaceIds, userIds)) {
            if (member.userId() != null) {
                members.put(key(member.workspaceId(), member.userId()), member);
            }
        }
        List<ConsentView> views = new ArrayList<>(rows.size());
        for (ConsentRepository.Row row : rows) {
            views.add(new ConsentView(
                    row.id(),
                    row.purpose(),
                    row.documentVersion(),
                    row.workspaceId(),
                    workspaceNames.get(row.workspaceId()),
                    nicknameOf(members, row.workspaceId(), row.userId()),
                    row.grantedBy() == null ? null : nicknameOf(members, row.workspaceId(), row.grantedBy()),
                    row.grantedAt(),
                    row.revokedAt(),
                    row.supersededAt(),
                    canRevoke(row, userId, members.get(key(row.workspaceId(), userId)))));
        }
        return views;
    }

    @Transactional
    void revoke(UUID userId, UUID consentId) {
        ConsentRepository.Row consent = consents.lock(consentId)
                .filter(row -> userId.equals(row.userId()) || userId.equals(row.grantedBy()))
                .orElseThrow(() -> new PrivacyRejectedException(404, "consent.not-found", NOT_FOUND_DETAIL));
        if (!userId.equals(consent.grantedBy())) {
            throw forbidden();
        }
        // Withdrawn already, or replaced by a newer consent that now covers the stay: nothing left to do.
        if (!consent.open()) {
            return;
        }
        Optional<MemberSummary> guardian =
                memberships.membersOf(List.of(consent.workspaceId()), List.of(userId)).stream()
                        .findFirst();
        if (!isActiveGuardian(guardian.orElse(null))) {
            throw forbidden();
        }
        consents.markRevoked(consentId);
        // The membership row outlives the youth's removal, so it is always there while the consent is.
        MemberSummary youth = memberships.membersOf(List.of(consent.workspaceId()), List.of(consent.userId())).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("A consent without its membership"));
        // Only a member still YOUTH goes; the check is part of the update, so a promotion in between wins.
        memberships.removeIfRole(
                consent.workspaceId(), youth.membershipId(), Role.YOUTH, userId, MemberRemoved.Reason.CONSENT_REVOKED);
        trail.record(new AuditEntry(
                consent.workspaceId(),
                userId,
                "consent.revoked",
                "MEMBERSHIP",
                youth.membershipId(),
                ConsentListener.PARENTAL_METADATA));
    }

    private static boolean canRevoke(ConsentRepository.Row row, UUID me, @Nullable MemberSummary myMembership) {
        return me.equals(row.grantedBy()) && row.open() && isActiveGuardian(myMembership);
    }

    private static boolean isActiveGuardian(@Nullable MemberSummary member) {
        return member != null
                && member.status() == MembershipStatus.ACTIVE
                && (member.role() == Role.OWNER || member.role() == Role.ADULT);
    }

    private static @Nullable String nicknameOf(Map<String, MemberSummary> members, UUID workspaceId, UUID userId) {
        MemberSummary member = members.get(key(workspaceId, userId));
        return member == null ? null : member.nickname();
    }

    private static String key(UUID workspaceId, UUID userId) {
        return workspaceId + ":" + userId;
    }

    private static PrivacyRejectedException forbidden() {
        return new PrivacyRejectedException(403, "consent.revoke-forbidden", FORBIDDEN_DETAIL);
    }
}
