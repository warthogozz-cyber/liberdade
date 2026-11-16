package br.com.liberdadefamilia.privacy.internal;

import br.com.liberdadefamilia.audit.AuditEntry;
import br.com.liberdadefamilia.audit.AuditTrail;
import br.com.liberdadefamilia.workspace.MemberRemoved;
import br.com.liberdadefamilia.workspace.MemberSummary;
import br.com.liberdadefamilia.workspace.MembershipStatus;
import br.com.liberdadefamilia.workspace.Memberships;
import br.com.liberdadefamilia.workspace.Role;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists the consents that involve a person and lets the guardian who gave one take it back. Once that guardian is gone
 * from the family, any active Owner of it may take it back instead.
 */
@Service
class ConsentService {

    private static final String NOT_FOUND_DETAIL = "Consentimento não encontrado.";
    private static final String FORBIDDEN_DETAIL = "Só quem deu o consentimento pode revogá-lo.";
    // A stable sort, so rows granted at the same instant keep the order the query gave them.
    private static final Comparator<ConsentRepository.Row> NEWEST_FIRST =
            Comparator.comparing(ConsentRepository.Row::grantedAt).reversed();

    private final ConsentRepository consents;
    private final Memberships memberships;
    private final AuditTrail trail;

    ConsentService(ConsentRepository consents, Memberships memberships, AuditTrail trail) {
        this.consents = consents;
        this.memberships = memberships;
        this.trail = trail;
    }

    /** The parental consents of the person, for the screen that lists them. Document acceptances are left out. */
    @Transactional(readOnly = true)
    List<ConsentView> list(UUID userId) {
        return views(userId, false);
    }

    /** Everything the person consented to, the acceptances of the terms and the privacy policy included. */
    @Transactional(readOnly = true)
    List<ConsentView> listForExport(UUID userId) {
        return views(userId, true);
    }

    private List<ConsentView> views(UUID userId, boolean withDocuments) {
        List<ConsentRepository.Row> rows = new ArrayList<>(consents.involving(userId));
        if (!withDocuments) {
            rows.removeIf(row -> row.purpose() != ConsentPurpose.PARENTAL);
        }
        rows.addAll(leftByTheirGranter(userId, rows));
        if (rows.isEmpty()) {
            return List.of();
        }
        rows.sort(NEWEST_FIRST);
        Set<UUID> workspaceIds = new HashSet<>();
        Set<UUID> userIds = new HashSet<>();
        userIds.add(userId);
        for (ConsentRepository.Row row : rows) {
            if (row.workspaceId() != null) {
                workspaceIds.add(row.workspaceId());
            }
            userIds.add(row.userId());
            if (row.grantedBy() != null) {
                userIds.add(row.grantedBy());
            }
        }
        Map<UUID, String> workspaceNames = memberships.workspaceNames(workspaceIds);
        Map<String, MemberSummary> members = membersByKey(workspaceIds, userIds);
        List<ConsentView> views = new ArrayList<>(rows.size());
        for (ConsentRepository.Row row : rows) {
            views.add(new ConsentView(
                    row.id(),
                    row.purpose(),
                    row.documentVersion(),
                    row.workspaceId(),
                    row.workspaceId() == null ? null : workspaceNames.get(row.workspaceId()),
                    nicknameOf(members, row.workspaceId(), row.userId()),
                    row.grantedBy() == null ? null : nicknameOf(members, row.workspaceId(), row.grantedBy()),
                    row.grantedAt(),
                    row.revokedAt(),
                    row.supersededAt(),
                    canRevoke(row, userId, members)));
        }
        return views;
    }

    @Transactional
    void revoke(UUID userId, UUID consentId) {
        ConsentRepository.Row consent = consents.lock(consentId)
                .orElseThrow(() -> new PrivacyRejectedException(404, "consent.not-found", NOT_FOUND_DETAIL));
        // Only the parental consent is the guardian's to take back; an acceptance of a document is not listed here.
        UUID workspaceId = consent.workspaceId();
        if (consent.purpose() != ConsentPurpose.PARENTAL || workspaceId == null) {
            throw new PrivacyRejectedException(404, "consent.not-found", NOT_FOUND_DETAIL);
        }
        boolean granter = userId.equals(consent.grantedBy());
        Map<String, MemberSummary> members = membersByKey(
                List.of(workspaceId),
                consent.grantedBy() == null ? List.of(userId) : List.of(userId, consent.grantedBy()));
        if (!granter && !mayStepIn(consent, userId, members)) {
            if (userId.equals(consent.userId())) {
                throw forbidden();
            }
            throw new PrivacyRejectedException(404, "consent.not-found", NOT_FOUND_DETAIL);
        }
        // Withdrawn already, or replaced by a newer consent that now covers the stay: nothing left to do.
        if (!consent.open()) {
            return;
        }
        if (granter && !isActiveGuardian(members.get(key(workspaceId, userId)))) {
            throw forbidden();
        }
        consents.markRevoked(consentId);
        // The membership row outlives the youth's removal, so it is always there while the consent is.
        MemberSummary youth = memberships.membersOf(List.of(workspaceId), List.of(consent.userId())).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("A consent without its membership"));
        // Only a member still YOUTH goes; the check is part of the update, so a promotion in between wins.
        memberships.removeIfRole(
                workspaceId, youth.membershipId(), Role.YOUTH, userId, MemberRemoved.Reason.CONSENT_REVOKED);
        trail.record(new AuditEntry(
                workspaceId,
                userId,
                "consent.revoked",
                "MEMBERSHIP",
                youth.membershipId(),
                ConsentListener.PARENTAL_METADATA));
    }

    /** The open consents of the families I own whose granter is gone: mine to take back in their place. */
    private List<ConsentRepository.Row> leftByTheirGranter(UUID me, List<ConsentRepository.Row> already) {
        Set<UUID> owned = new HashSet<>();
        for (MemberSummary member : memberships.membershipsOf(me)) {
            if (isActiveOwner(member)) {
                owned.add(member.workspaceId());
            }
        }
        List<ConsentRepository.Row> open = consents.openIn(owned);
        if (open.isEmpty()) {
            return List.of();
        }
        Set<UUID> people = new HashSet<>();
        people.add(me);
        open.stream()
                .map(ConsentRepository.Row::grantedBy)
                .filter(Objects::nonNull)
                .forEach(people::add);
        Map<String, MemberSummary> members = membersByKey(owned, people);
        Set<UUID> seen = new HashSet<>();
        already.forEach(row -> seen.add(row.id()));
        return open.stream()
                .filter(row -> !seen.contains(row.id()) && mayStepIn(row, me, members))
                .toList();
    }

    private Map<String, MemberSummary> membersByKey(Collection<UUID> workspaceIds, Collection<UUID> userIds) {
        Map<String, MemberSummary> members = new HashMap<>();
        for (MemberSummary member : memberships.membersOf(workspaceIds, userIds)) {
            if (member.userId() != null) {
                members.put(key(member.workspaceId(), member.userId()), member);
            }
        }
        return members;
    }

    private static boolean canRevoke(ConsentRepository.Row row, UUID me, Map<String, MemberSummary> members) {
        UUID workspaceId = row.workspaceId();
        if (!row.open() || workspaceId == null) {
            return false;
        }
        if (me.equals(row.grantedBy())) {
            return isActiveGuardian(members.get(key(workspaceId, me)));
        }
        return mayStepIn(row, me, members);
    }

    /**
     * Whether I may revoke in the granter's place: the granter has left the family, or their account is gone, and I am
     * an active Owner of it. The youth the consent is about never may.
     */
    private static boolean mayStepIn(ConsentRepository.Row row, UUID me, Map<String, MemberSummary> members) {
        UUID workspaceId = row.workspaceId();
        if (workspaceId == null || me.equals(row.userId()) || !isActiveOwner(members.get(key(workspaceId, me)))) {
            return false;
        }
        if (row.grantedBy() == null) {
            return true;
        }
        MemberSummary granter = members.get(key(workspaceId, row.grantedBy()));
        return granter == null || granter.status() != MembershipStatus.ACTIVE;
    }

    private static boolean isActiveOwner(@Nullable MemberSummary member) {
        return member != null && member.status() == MembershipStatus.ACTIVE && member.role() == Role.OWNER;
    }

    private static boolean isActiveGuardian(@Nullable MemberSummary member) {
        return member != null
                && member.status() == MembershipStatus.ACTIVE
                && (member.role() == Role.OWNER || member.role() == Role.ADULT);
    }

    private static @Nullable String nicknameOf(
            Map<String, MemberSummary> members, @Nullable UUID workspaceId, UUID userId) {
        if (workspaceId == null) {
            return null;
        }
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
