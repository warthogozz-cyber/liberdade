package br.com.liberdadefamilia.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** The passkeys a user has registered. */
public interface Passkeys {

    /** When the user's newest passkey was registered; empty when they have none. */
    Optional<Instant> latestCreatedAt(UUID userId);
}
