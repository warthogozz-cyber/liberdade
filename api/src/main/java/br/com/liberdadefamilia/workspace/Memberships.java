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

    Optional<MemberSummary> find(UUID workspaceId, UUID membershipId);

    /** Every membership of the family, active and removed. */
    List<MemberSummary> all(UUID workspaceId);

    /** Every membership of the person, active and removed. */
    List<MemberSummary> membershipsOf(UUID userId);

    /** The names of the families, in one query. A family that does not exist is left out. */
    Map<UUID, String> workspaceNames(Collection<UUID> workspaceIds);

    /** Whether some family without a pending deletion has this person as its only active OWNER. */
    boolean isSoleActiveOwnerOfLiveFamily(UUID userId);

    /**
     * Ends the membership and publishes {@link MemberRemoved}. Ending one that already ended changes nothing and
     * publishes nothing. It does not look at who is left: the caller keeps the family from losing its last OWNER.
     */
    void remove(UUID workspaceId, UUID membershipId, @Nullable UUID actorUserId, MemberRemoved.Reason reason);

    /** Ends every ACTIVE membership of the person, with the reason {@code ACCOUNT_DELETED}. */
    void detachUser(UUID userId);
}
