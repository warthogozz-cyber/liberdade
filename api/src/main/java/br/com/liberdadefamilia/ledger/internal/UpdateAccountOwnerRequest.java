package br.com.liberdadefamilia.ledger.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Who an account belongs to; null takes the owner away. */
record UpdateAccountOwnerRequest(
        @Nullable @Schema(nullable = true) UUID membershipId) {}
