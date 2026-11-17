package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.identity.PasskeySummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A passkey in the API.
 *
 * @param id the credential id, base64url
 * @param lastUsedAt null when the passkey never signed the person in
 */
record PasskeyView(
        String id,
        String label,
        Instant createdAt,
        @Nullable @Schema(nullable = true) Instant lastUsedAt) {

    static PasskeyView of(PasskeySummary summary) {
        return new PasskeyView(summary.id(), summary.label(), summary.createdAt(), summary.lastUsedAt());
    }
}
