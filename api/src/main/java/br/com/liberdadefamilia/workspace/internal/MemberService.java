package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.identity.UserDirectory;
import br.com.liberdadefamilia.identity.UserSummary;
import br.com.liberdadefamilia.workspace.MemberProfileChanged;
import br.com.liberdadefamilia.workspace.MemberRemoved;
import br.com.liberdadefamilia.workspace.MemberRoleChanged;
import br.com.liberdadefamilia.workspace.MembershipStatus;
import br.com.liberdadefamilia.workspace.Memberships;
import br.com.liberdadefamilia.workspace.Role;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.internal.MemberRepository.MemberRow;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists the family's people and lets an owner change or remove them. Error messages and logs never carry a name. The
 * rule that a family always keeps an active Owner is checked by {@link ActiveOwners}, with the Owners' rows locked.
 */
@Service
class MemberService {

    private final MemberRepository members;
    private final ActiveOwners activeOwners;
    private final Memberships memberships;
    private final UserDirectory users;
    private final ApplicationEventPublisher events;

    MemberService(
            MemberRepository members,
            ActiveOwners activeOwners,
            Memberships memberships,
            UserDirectory users,
            ApplicationEventPublisher events) {
        this.members = members;
        this.activeOwners = activeOwners;
        this.memberships = memberships;
        this.users = users;
        this.events = events;
    }

    /** Active members first, then by nickname (or the name when there is none), then by id. */
    @Transactional(readOnly = true)
    List<MemberView> list(WorkspaceMember caller) {
        return views(members.all(caller.workspaceId()), caller);
    }

    /**
     * Applies the change and publishes {@link MemberRoleChanged} when the role moved and {@link MemberProfileChanged} when the
     * nickname did.
     *
     * {@code requireRecentSignIn} runs only when the role really changes, decided here with the owners locked.
     *
     * @throws MemberNotFoundException when the membership is not in the family
     * @throws WorkspaceRejectedException when the member is gone, the last active Owner would step down, or a YOUTH role
     *     comes without the guardian's consent or for a person who signs in with Google
     */
    @Transactional
    MemberView update(
            WorkspaceMember caller, UUID membershipId, UpdateMemberRequest request, Runnable requireRecentSignIn) {
        UUID workspaceId = caller.workspaceId();
        List<ActiveOwners.Owner> owners = activeOwners.lock(List.of(workspaceId));
        MemberRow target = members.find(workspaceId, membershipId).orElseThrow(MemberNotFoundException::new);
        if (target.status() == MembershipStatus.REMOVED) {
            throw new WorkspaceRejectedException("member.removed", "Essa pessoa já saiu da família.");
        }
        Role newRole = request.role() == null ? target.role() : request.role();
        boolean roleChanges = newRole != target.role();
        if (roleChanges) {
            requireRecentSignIn.run();
        }
        if (roleChanges && target.role() == Role.OWNER && noOtherOwner(owners, membershipId)) {
            throw lastOwner();
        }
        UUID consentBy = null;
        if (roleChanges && newRole == Role.YOUTH) {
            if (membershipId.equals(caller.membershipId())) {
                throw new WorkspaceRejectedException(
                        "member.self-youth", "Você não pode mudar o seu próprio papel para Jovem.");
            }
            if (!Boolean.TRUE.equals(request.parentalConsent())) {
                throw new WorkspaceRejectedException(
                        "member.parental-consent-required",
                        "Para ser Jovem, é preciso o consentimento do responsável.");
            }
            UserSummary person = target.userId() == null
                    ? null
                    : users.findAll(List.of(target.userId())).get(target.userId());
            if (person != null && person.hasGoogle()) {
                throw new WorkspaceRejectedException(
                        "member.youth-needs-passkey",
                        "Quem entra com o Google não pode ser Jovem: o Jovem entra só com passkey.");
            }
            consentBy = caller.userId();
        }
        members.update(workspaceId, membershipId, roleChanges ? newRole : null, request.nickname(), consentBy);
        if (request.nickname() != null && !request.nickname().equals(target.nickname())) {
            events.publishEvent(new MemberProfileChanged(workspaceId, membershipId));
        }
        if (roleChanges) {
            events.publishEvent(
                    new MemberRoleChanged(workspaceId, membershipId, target.role(), newRole, caller.userId()));
        }
        MemberRow updated = members.find(workspaceId, membershipId).orElseThrow(MemberNotFoundException::new);
        return views(List.of(updated), caller).getFirst();
    }

    /**
     * Removes the member. Removing one who is already gone changes nothing.
     *
     * @throws MemberNotFoundException when the membership is not in the family
     * @throws WorkspaceRejectedException when it is the family's last active Owner
     */
    @Transactional
    void remove(WorkspaceMember caller, UUID membershipId) {
        UUID workspaceId = caller.workspaceId();
        List<ActiveOwners.Owner> owners = activeOwners.lock(List.of(workspaceId));
        MemberRow target = members.find(workspaceId, membershipId).orElseThrow(MemberNotFoundException::new);
        if (target.status() == MembershipStatus.REMOVED) {
            return;
        }
        if (target.role() == Role.OWNER && noOtherOwner(owners, membershipId)) {
            throw lastOwner();
        }
        memberships.remove(workspaceId, membershipId, caller.userId(), MemberRemoved.Reason.BY_OWNER);
    }

    /** Whether the family would be left without an active Owner once this member is not one. */
    private static boolean noOtherOwner(List<ActiveOwners.Owner> owners, UUID membershipId) {
        return owners.stream().allMatch(owner -> owner.membershipId().equals(membershipId));
    }

    private static WorkspaceRejectedException lastOwner() {
        return new WorkspaceRejectedException("member.last-owner", "A família precisa de pelo menos uma Dona ou Dono.");
    }

    private List<MemberView> views(List<MemberRow> rows, WorkspaceMember caller) {
        Set<UUID> userIds =
                rows.stream().map(MemberRow::userId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, UserSummary> people = users.findAll(userIds);
        Collator collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
        return rows.stream()
                .map(row -> new Entry(row, row.userId() == null ? null : people.get(row.userId())))
                .sorted(Comparator.<Entry, Boolean>comparing(
                                entry -> entry.row().status() != MembershipStatus.ACTIVE)
                        .thenComparing(Entry::sortName, collator)
                        .thenComparing(entry -> entry.row().id()))
                .map(entry -> new MemberView(
                        entry.row().id(),
                        entry.person() == null ? null : entry.person().displayName(),
                        entry.row().nickname(),
                        entry.row().role(),
                        entry.row().status(),
                        entry.row().id().equals(caller.membershipId()),
                        entry.person() != null && !entry.person().hasGoogle(),
                        entry.row().joinedAt()))
                .toList();
    }

    /** A member with the person behind them, if the account still exists. */
    private record Entry(MemberRow row, @Nullable UserSummary person) {

        /** The nickname, or the display name when there is none: what the list is ordered by. */
        String sortName() {
            String name = row.nickname() != null ? row.nickname() : person == null ? null : person.displayName();
            return name == null ? "" : name;
        }
    }
}
