package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.InvitationKind;
import br.com.liberdadefamilia.workspace.Role;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A pending link as the family sees it.
 *
 * @param createdByName the creator's nickname in this family; null once their account is deleted
 * @param token the secret, only in the answer that creates the link; the key is left out of every other answer
 */
record InvitationView(
        UUID id,
        Role role,
        @Nullable @Schema(nullable = true) String nickname,
        InvitationKind kind,
        Instant expiresAt,
        @Nullable @Schema(nullable = true) String createdByName,

        @Nullable @Schema(nullable = true) @JsonInclude(JsonInclude.Include.NON_NULL)
        String token) {

    InvitationView withToken(String secret) {
        return new InvitationView(id, role, nickname, kind, expiresAt, createdByName, secret);
    }
}
