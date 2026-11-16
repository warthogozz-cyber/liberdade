package br.com.liberdadefamilia.identity;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The signed-in person. Any controller gets it by declaring a parameter of this type.
 *
 * @param familyNameHint the family name Google sent, used to suggest a family name; {@code null} for passkey sign-ins
 * @param deletionRequestedAt when the person asked for their account to be deleted; {@code null} when they did not
 */
public record CurrentUser(
        UUID id,
        String displayName,
        @Nullable String familyNameHint,
        @Nullable Instant deletionRequestedAt) {

    /** The day the account will be erased, while a deletion request is pending. */
    public @Nullable Instant deletionScheduledFor() {
        return deletionRequestedAt == null ? null : deletionRequestedAt.plus(UserDeletion.GRACE);
    }
}
