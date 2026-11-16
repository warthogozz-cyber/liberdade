package br.com.liberdadefamilia.workspace;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A membership as the other modules see it. {@code userId} is null once the person's account is gone.
 *
 * @param nickname how the family calls the person; never an email
 */
public record MemberSummary(
        UUID workspaceId,
        UUID membershipId,
        @Nullable UUID userId,
        Role role,
        MembershipStatus status,
        @Nullable String nickname) {}
