package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.identity.CurrentUser;
import br.com.liberdadefamilia.identity.UserDirectory;
import br.com.liberdadefamilia.workspace.InvitationCreated;
import br.com.liberdadefamilia.workspace.InvitationKind;
import br.com.liberdadefamilia.workspace.InvitationRevoked;
import br.com.liberdadefamilia.workspace.MemberJoined;
import br.com.liberdadefamilia.workspace.MembershipStatus;
import br.com.liberdadefamilia.workspace.Role;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.internal.InvitationRepository.InvitationRow;
import br.com.liberdadefamilia.workspace.internal.MemberRepository.MemberRow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single-use invitations. A token is a secret: it is hashed on the way in, looked up by its hash, and neither it nor
 * the hash is ever logged or put in an error. Every instant comes from the application clock.
 */
@Service
class InvitationService {

    static final Duration VALIDITY = Duration.ofDays(7);

    private final InvitationRepository invitations;
    private final MemberRepository members;
    private final UserDirectory users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    InvitationService(
            InvitationRepository invitations,
            MemberRepository members,
            UserDirectory users,
            ApplicationEventPublisher events,
            Clock clock) {
        this.invitations = invitations;
        this.members = members;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Makes a link that lasts 7 days and returns it with its token, the only time the token is shown.
     *
     * @throws InvitationForbiddenException when an ADULT invites an OWNER
     * @throws WorkspaceRejectedException when a YOUTH comes without the guardian's consent
     */
    @Transactional
    InvitationView create(WorkspaceMember caller, CreateInvitationRequest request) {
        Role role = request.role();
        if (role == Role.OWNER && caller.role() != Role.OWNER) {
            throw new InvitationForbiddenException();
        }
        Instant now = clock.instant();
        Instant consentAt = null;
        UUID consentBy = null;
        if (role == Role.YOUTH) {
            if (!Boolean.TRUE.equals(request.parentalConsent())) {
                throw new WorkspaceRejectedException(
                        "invitation.parental-consent-required",
                        "Para convidar um Jovem, é preciso o consentimento do responsável.");
            }
            consentAt = now;
            consentBy = caller.userId();
        }
        String token = InvitationTokens.newToken();
        Instant expiresAt = now.plus(VALIDITY);
        UUID id = invitations.insert(
                caller.workspaceId(),
                InvitationKind.INVITE,
                role,
                request.nickname(),
                consentAt,
                consentBy,
                InvitationTokens.hash(token),
                caller.userId(),
                expiresAt);
        events.publishEvent(
                new InvitationCreated(caller.workspaceId(), id, InvitationKind.INVITE, role, caller.userId()));
        String createdByName = members.find(caller.workspaceId(), caller.membershipId())
                .map(MemberRow::nickname)
                .orElse(null);
        return new InvitationView(id, role, request.nickname(), InvitationKind.INVITE, expiresAt, createdByName, token);
    }

    @Transactional(readOnly = true)
    List<InvitationView> pending(WorkspaceMember caller) {
        return invitations.pending(caller.workspaceId(), clock.instant());
    }

    /**
     * Cancels a pending link and publishes {@link InvitationRevoked}. Cancelling one that is already cancelled or used
     * changes nothing and publishes nothing.
     *
     * @throws InvitationNotFoundException when the invitation is not in this family
     */
    @Transactional
    void revoke(WorkspaceMember caller, UUID invitationId) {
        if (invitations.revoke(caller.workspaceId(), invitationId, clock.instant())) {
            events.publishEvent(new InvitationRevoked(caller.workspaceId(), invitationId, caller.userId()));
        } else if (!invitations.exists(caller.workspaceId(), invitationId)) {
            throw new InvitationNotFoundException();
        }
    }

    /**
     * Shows what the link is for without using it up.
     *
     * @throws InvitationNotFoundException when no link has this token
     * @throws InvitationGoneException when it was used, cancelled or has expired
     */
    @Transactional(readOnly = true)
    InvitationPreview preview(String token) {
        InvitationRow row =
                invitations.findByHash(InvitationTokens.hash(token)).orElseThrow(InvitationNotFoundException::new);
        if (row.isGone(clock.instant())) {
            throw new InvitationGoneException();
        }
        return new InvitationPreview(row.workspaceName(), row.createdByName(), row.role(), row.nickname(), row.kind());
    }

    /**
     * Takes the link, all in one transaction with the link's row locked: of two people taking the same link, the
     * second finds it used. A person with no session must say who they are, and gets a new account; the session is opened by the caller after the commit.
     *
     * @throws InvitationNotFoundException when no link has this token
     * @throws InvitationGoneException when it was used, cancelled or has expired
     * @throws WorkspaceRejectedException when nobody is signed in and no new person was described
     * @throws InvitationConflictException when the person is already an active member
     */
    @Transactional
    AcceptResult accept(AcceptInvitationRequest request, Optional<CurrentUser> caller) {
        InvitationRow row = invitations
                .lockByHash(InvitationTokens.hash(request.token()))
                .orElseThrow(InvitationNotFoundException::new);
        Instant now = clock.instant();
        if (row.isGone(now)) {
            throw new InvitationGoneException();
        }
        if (row.kind() != InvitationKind.INVITE || row.nickname() == null) {
            // Recovery links bring someone back into their own place; they are taken by their own route.
            throw new InvitationNotFoundException();
        }
        AcceptInvitationRequest.NewUser newUser = request.newUser();
        UUID userId;
        if (caller.isPresent()) {
            userId = caller.get().id();
        } else if (newUser == null) {
            throw new WorkspaceRejectedException(
                    "invitation.new-user-required", "Diga como você se chama para entrar na família.");
        } else {
            userId = users.createPasskeyOnlyUser(newUser.displayName());
        }
        Optional<MemberRow> existing = members.findByUser(row.workspaceId(), userId);
        if (existing.isPresent() && existing.get().status() == MembershipStatus.ACTIVE) {
            throw new InvitationConflictException();
        }
        Instant consentAt = row.role() == Role.YOUTH ? row.consentAt() : null;
        UUID consentBy = row.role() == Role.YOUTH ? row.consentBy() : null;
        UUID membershipId;
        if (existing.isPresent()) {
            membershipId = existing.get().id();
            members.reactivate(row.workspaceId(), membershipId, row.role(), row.nickname(), consentAt, consentBy);
        } else {
            membershipId = members.insert(row.workspaceId(), userId, row.role(), row.nickname(), consentAt, consentBy);
        }
        invitations.markUsed(row.id(), now);
        events.publishEvent(
                new MemberJoined(row.workspaceId(), membershipId, userId, row.role(), row.nickname(), consentBy));
        boolean created = caller.isEmpty();
        return new AcceptResult(
                new AcceptedInvitation(row.workspaceId(), membershipId, created), created ? userId : null);
    }

    /**
     * What taking a link gives back.
     *
     * @param signInUserId the person this call created, who the caller must now sign in once the transaction has
     *     committed; null when the caller already had a session
     */
    record AcceptResult(
            AcceptedInvitation accepted, @Nullable UUID signInUserId) {}
}
