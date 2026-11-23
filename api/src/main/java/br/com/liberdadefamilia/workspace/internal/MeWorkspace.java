package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.shared.PaymentMethod;
import br.com.liberdadefamilia.workspace.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A family the user is an active member of, as {@code GET /v1/me} lists it. {@code gettingStartedDismissedAt} is this
 * member's own; {@code hasTransactions} is the family's, and so is
 * {@code familyInvited}: whether two or more people are in it. {@code recoveredAt} is when a recovery link last brought
 * this member back in, if one did. {@code deletionScheduledFor} is set only for an Owner, on a family that asked to be
 * deleted: the day it goes. {@code country} and {@code locale} are the family's, and {@code paymentMethods} is what its
 * country offers, in the order to list them.
 */
record MeWorkspace(
        UUID id,
        String name,
        Role role,
        UUID membershipId,
        String timezone,
        String currency,
        String country,
        String locale,
        List<PaymentMethod> paymentMethods,
        @Nullable @Schema(nullable = true) Instant gettingStartedDismissedAt,
        boolean hasTransactions,
        boolean familyInvited,
        @Nullable @Schema(nullable = true) Instant recoveredAt,
        @Nullable @Schema(nullable = true) Instant deletionScheduledFor) {}
