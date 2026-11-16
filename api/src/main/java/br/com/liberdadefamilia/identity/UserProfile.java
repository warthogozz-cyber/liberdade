package br.com.liberdadefamilia.identity;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * What a person may take with them (data portability): their own record, email included. Only the person's own
 * export may carry it; no other module hands it to anyone else.
 *
 * @param passkeyCreatedAt when the newest passkey was registered; null without one
 */
public record UserProfile(
        String displayName,
        @Nullable String email,
        String locale,
        Instant createdAt,
        @Nullable Instant passkeyCreatedAt) {}
