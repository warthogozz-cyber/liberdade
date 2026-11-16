package br.com.liberdadefamilia.workspace;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Someone entered the family through an invitation. Published inside the accepting transaction.
 *
 * @param parentalConsentBy the guardian who vouched for a YOUTH member; null for the other roles
 */
public record MemberJoined(
        UUID workspaceId,
        UUID membershipId,
        UUID userId,
        Role role,
        String nickname,
        @Nullable UUID parentalConsentBy) {}
