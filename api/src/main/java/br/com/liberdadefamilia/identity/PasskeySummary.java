package br.com.liberdadefamilia.identity;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * One of a person's passkeys, as the person sees it.
 *
 * @param id the credential id, base64url, the way Spring Security stores it
 * @param label the name the person gave the passkey
 * @param lastUsedAt when it last signed the person in; null when it never did
 */
public record PasskeySummary(
        String id,
        String label,
        Instant createdAt,
        @Nullable @Schema(nullable = true) Instant lastUsedAt) {}
