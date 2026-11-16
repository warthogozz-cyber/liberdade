package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.MembershipStatus;
import br.com.liberdadefamilia.workspace.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A member as the family sees them. There is no email here, on purpose: nobody sees another person's email.
 *
 * @param id the membership id
 * @param displayName null once the person's account is deleted
 * @param you whether this is the caller
 * @param passkeyOnly whether the person has no Google sign-in, so only an owner's recovery link brings them back
 */
record MemberView(
        UUID id,
        @Nullable @Schema(nullable = true) String displayName,
        @Nullable @Schema(nullable = true) String nickname,
        Role role,
        MembershipStatus status,
        boolean you,
        boolean passkeyOnly,
        Instant joinedAt) {}
