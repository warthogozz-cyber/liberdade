package br.com.liberdadefamilia.markets.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * The family's links for a market. It always carries all five fields: a {@code null} one deletes the family's value,
 * and the card falls back to OpenStreetMap's, if it has one.
 */
record LinksRequest(
        @Nullable @Schema(nullable = true) String website,
        @Nullable @Schema(nullable = true) String instagram,
        @Nullable @Schema(nullable = true) String flyerUrl,

        @Nullable @Size(max = 60) @Schema(nullable = true, maxLength = 60)
        String flyerSource,

        @Nullable @Schema(nullable = true) LocalDate flyerValidUntil) {}
