package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.identity.CurrentUser;
import br.com.liberdadefamilia.identity.SignInMethod;
import br.com.liberdadefamilia.identity.UserDirectory;
import br.com.liberdadefamilia.identity.UserSummary;
import br.com.liberdadefamilia.workspace.InvitationCreated;
import br.com.liberdadefamilia.workspace.InvitationKind;
import br.com.liberdadefamilia.workspace.InvitationRevoked;
import br.com.liberdadefamilia.workspace.MemberJoined;
import br.com.liberdadefamilia.workspace.MembershipStatus;
import br.com.liberdadefamilia.workspace.RecoveryCreated;
import br.com.liberdadefamilia.workspace.RecoveryUsed;
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
    static final Duration RECOVERY_VALIDITY = Duration.ofHours(24);

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
                expiresAt,
                null);
        events.publishEvent(
                new InvitationCreated(caller.workspaceId(), id, InvitationKind.INVITE, role, caller.userId()));
        String createdByName = members.find(caller.workspaceId(), caller.membershipId())
                .map(MemberRow::nickname)
                .orElse(null);
        return new InvitationView(id, role, request.nickname(), InvitationKind.INVITE, expiresAt, createdByName, token);
    }

    /**
     * Makes a link that lasts 24 hours to bring a passkey-only member back into their account, and returns it with its
     * token, the only time the token is shown. A link still pending for the same member is cancelled. The member row
     * is locked, so two links made at once leave one pending.
     *
     * @throws MemberNotFoundException when the membership is not in the family
     * @throws InvitationForbiddenException when an ADULT recovers an OWNER
     * @throws WorkspaceRejectedException when the member is gone, or signs in with Google
     */
    @Transactional
    InvitationView createRecovery(WorkspaceMember caller, UUID membershipId) {
        UUID workspaceId = caller.workspaceId();
        // Lock order: the pending links, then the member row, the same as taking a link, so the two cannot deadlock.
        invitations.lockPendingRecovery(workspaceId, membershipId);
        MemberRow target = members.lock(workspaceId, membershipId).orElseThrow(MemberNotFoundException::new);
        if (target.role() == Role.OWNER && caller.role() != Role.OWNER) {
            throw new InvitationForbiddenException();
        }
        if (target.status() != MembershipStatus.ACTIVE) {
            throw new WorkspaceRejectedException("member.removed", "Essa pessoa já saiu da família.");
        }
        UserSummary person = target.userId() == null
                ? null
                : users.findAll(List.of(target.userId())).get(target.userId());
        if (person == null || person.hasGoogle()) {
            throw new WorkspaceRejectedException(
                    "member.recovery-unavailable",
                    "Essa pessoa entra com o Google, então não há link de recuperação para ela.");
        }
        Instant now = clock.instant();
        invitations.revokePendingRecovery(workspaceId, membershipId, now);
        String token = InvitationTokens.newToken();
        Instant expiresAt = now.plus(RECOVERY_VALIDITY);
        UUID id = invitations.insert(
                workspaceId,
                InvitationKind.RECOVERY,
                target.role(),
                null,
                null,
                null,
                InvitationTokens.hash(token),
                caller.userId(),
                expiresAt,
                membershipId);
        events.publishEvent(new RecoveryCreated(workspaceId, id, membershipId, caller.userId()));
        String createdByName = members.find(workspaceId, caller.membershipId())
                .map(MemberRow::nickname)
                .orElse(null);
        return new InvitationView(id, target.role(), null, InvitationKind.RECOVERY, expiresAt, createdByName, token);
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
        if (row.isGone(clock.instant())
                || (row.kind() == InvitationKind.RECOVERY && (!row.targetActive() || row.targetRoleChanged()))) {
            throw new InvitationGoneException();
        }
        String nickname = row.kind() == InvitationKind.RECOVERY ? row.targetNickname() : row.nickname();
        return new InvitationPreview(row.workspaceName(), row.createdByName(), row.role(), nickname, row.kind());
    }

    /**
     * Takes the link, all in one transaction with the link's row locked: of two people taking the same link, the
     * second finds it used. The link also stops working when whoever made it is no longer an active OWNER or ADULT
     * (an OWNER, for an OWNER link). An INVITE link adds the caller as a member: a person with no session must say who
     * they are, and gets a new account. A RECOVERY link adds nobody: it signs in as the target, ignoring any
     * {@code newUser} and any session the caller had. In both cases the session is opened by the caller after the
     * commit.
     *
     * @throws InvitationNotFoundException when no link has this token
     * @throws InvitationGoneException when it was used, cancelled or has expired
     * @throws WorkspaceRejectedException when nobody is signed in and no new person was described, or a YOUTH link is
     *     taken by a signed-in person who has Google
     * @throws InvitationConflictException when the person is already an active member
     */
    @Transactional
    AcceptResult accept(AcceptInvitationRequest request, Optional<CurrentUser> caller) {
        InvitationRow row = invitations
                .lockByHash(InvitationTokens.hash(request.token()))
                .orElseThrow(InvitationNotFoundException::new);
        Instant now = clock.instant();
        if (row.isGone(now) || !creatorStillMayBringIn(row)) {
            throw new InvitationGoneException();
        }
        if (row.kind() == InvitationKind.RECOVERY) {
            return acceptRecovery(row, now);
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
        if (row.role() == Role.YOUTH && caller.isPresent() && hasGoogle(userId)) {
            throw new WorkspaceRejectedException(
                    "invitation.youth-needs-passkey",
                    "Convites de Jovem só podem ser aceitos com uma passkey nova. Saia da sua conta para continuar.");
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
                new AcceptedInvitation(row.workspaceId(), membershipId, created),
                created ? userId : null,
                SignInMethod.INVITATION);
    }

    /** Whether the person signs in with Google: a YOUTH signs in only with a passkey. */
    private boolean hasGoogle(UUID userId) {
        UserSummary person = users.findAll(List.of(userId)).get(userId);
        return person != null && person.hasGoogle();
    }

    /**
     * Whether whoever made the link may still bring in this role: an active OWNER or ADULT, and an OWNER when the link
     * is for an OWNER. Read after the link is locked, so it is the state this transaction sees.
     */
    private boolean creatorStillMayBringIn(InvitationRow row) {
        MemberRow creator =
                members.findByUser(row.workspaceId(), row.createdBy()).orElse(null);
        if (creator == null || creator.status() != MembershipStatus.ACTIVE) {
            return false;
        }
        if (creator.role() == Role.OWNER) {
            return true;
        }
        return creator.role() == Role.ADULT && row.role() != Role.OWNER;
    }

    /**
     * Brings the target back in. Whoever holds the link is signed in as the target, replacing any session they had, and
     * no person is created: a {@code newUser} in the request is ignored. The target row is locked, so a member removed
     * at the same moment is either seen as removed here or removed after this commits.
     */
    private AcceptResult acceptRecovery(InvitationRow row, Instant now) {
        UUID targetId = row.targetMembershipId();
        MemberRow target = targetId == null
                ? null
                : members.lock(row.workspaceId(), targetId).orElse(null);
        if (target == null
                || target.status() != MembershipStatus.ACTIVE
                || target.userId() == null
                || target.role() != row.role()) {
            throw new InvitationGoneException();
        }
        members.markRecovered(row.workspaceId(), target.id(), now);
        invitations.markUsed(row.id(), now);
        events.publishEvent(new RecoveryUsed(row.workspaceId(), target.id(), target.userId()));
        return new AcceptResult(
                new AcceptedInvitation(row.workspaceId(), target.id(), true), target.userId(), SignInMethod.RECOVERY);
    }

    /**
     * What taking a link gives back.
     *
     * @param signInUserId the person the caller must now sign in once the transaction has committed: the one this call
     *     created, or the target of a recovery; null when the caller already had a session and keeps it
     * @param signInMethod how that person is signed in
     */
    record AcceptResult(
            AcceptedInvitation accepted, @Nullable UUID signInUserId, SignInMethod signInMethod) {}
}
