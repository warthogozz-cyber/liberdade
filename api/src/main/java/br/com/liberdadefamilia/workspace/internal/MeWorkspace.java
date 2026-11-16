package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A family the user is an active member of, as {@code GET /v1/me} lists it. {@code gettingStartedDismissedAt} is this
 * member's own; {@code hasTransactions} is the family's, and so is
 * {@code familyInvited}: whether two or more people are in it.
 */
record MeWorkspace(
        UUID id,
        String name,
        Role role,
        UUID membershipId,
        String timezone,
        String currency,
        @Nullable @Schema(nullable = true) Instant gettingStartedDismissedAt,
        boolean hasTransactions,
        boolean familyInvited) {}
