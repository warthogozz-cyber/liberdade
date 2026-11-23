package br.com.liberdadefamilia.workspace;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Who belongs to which family. The one way for other modules to read or end a membership. */
public interface Memberships {

    /** Whether the membership is an ACTIVE one of this family. */
    boolean isActive(UUID workspaceId, UUID membershipId);

    /** The membership as the caller of a request would be, if it is an ACTIVE one of this family; else empty. */
    Optional<WorkspaceMember> activeMember(UUID workspaceId, UUID membershipId);

    /** The family's oldest ACTIVE OWNER, as a member; empty when the family has none. */
    Optional<WorkspaceMember> activeOwner(UUID workspaceId);

    Optional<MemberSummary> find(UUID workspaceId, UUID membershipId);

    /** Every membership of the family, active and removed. */
    List<MemberSummary> all(UUID workspaceId);

    /** Every membership of the person, active and removed. */
    List<MemberSummary> membershipsOf(UUID userId);

    /** The memberships of these people in these families, active or removed, in one query. */
    List<MemberSummary> membersOf(Collection<UUID> workspaceIds, Collection<UUID> userIds);

    /** The family's country and language; empty when the family does not exist. */
    Optional<WorkspaceRegion> regionOf(UUID workspaceId);

    /** The names of the families, in one query. A family that does not exist is left out. */
    Map<UUID, String> workspaceNames(Collection<UUID> workspaceIds);

    /**
     * Whether some family without a pending deletion has this person as its only active Owner: no other active OWNER
     * membership whose person keeps their account. Locks those families' Owner rows until the caller's transaction
     * ends, so two co-owners asking at once cannot both leave the family without one.
     */
    boolean isSoleActiveOwnerOfLiveFamily(UUID userId);

    /**
     * Ends the membership and publishes {@link MemberRemoved}. Ending one that already ended changes nothing and
     * publishes nothing. It does not look at who is left: the caller keeps the family from losing its last OWNER.
     */
    void remove(UUID workspaceId, UUID membershipId, @Nullable UUID actorUserId, MemberRemoved.Reason reason);

    /**
     * Ends the membership only if it is ACTIVE and still has the expected role, in one atomic update, and publishes
     * {@link MemberRemoved} only when it did. A role changed in the meantime wins: nothing happens.
     *
     * @return whether the membership was ended
     */
    boolean removeIfRole(
            UUID workspaceId,
            UUID membershipId,
            Role expected,
            @Nullable UUID actorUserId,
            MemberRemoved.Reason reason);

    /** Ends every ACTIVE membership of the person, with the reason {@code ACCOUNT_DELETED}. */
    void detachUser(UUID userId);
}
