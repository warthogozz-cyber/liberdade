package br.com.liberdadefamilia.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The passkeys a user has registered. */
public interface Passkeys {

    /** When the user's newest passkey was registered; empty when they have none. */
    Optional<Instant> latestCreatedAt(UUID userId);

    /** The user's passkeys, newest first. */
    List<PasskeySummary> list(UUID userId);

    /**
     * Removes one of the user's own passkeys and publishes {@link PasskeyRemoved}. The check and the delete share a
     * transaction that locks the user's passkeys, so two removals at once cannot take the last two.
     *
     * @param credentialId the credential id, base64url
     * @throws br.com.liberdadefamilia.shared.DomainException answering 404 {@code passkey.not-found} when the passkey
     *     is not the user's, and 422 {@code passkey.last-method} when it is their last passkey and they have no Google
     *     sign-in
     */
    void remove(UUID userId, String credentialId);
}
