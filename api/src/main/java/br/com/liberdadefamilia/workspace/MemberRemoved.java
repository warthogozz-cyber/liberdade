package br.com.liberdadefamilia.workspace;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A member left the family. Published inside the removing transaction.
 *
 * @param actorUserId who removed the member; null when nobody did, as when the account is deleted
 */
public record MemberRemoved(
        UUID workspaceId, UUID membershipId, @Nullable UUID actorUserId, Reason reason) {

    /** Why the membership ended. */
    public enum Reason {
        BY_OWNER,
        CONSENT_REVOKED,
        ACCOUNT_DELETED
    }
}
