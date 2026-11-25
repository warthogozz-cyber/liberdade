package br.com.liberdadefamilia.markets.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** A new position for a family market: both coordinates, at most 5 decimals. Absent ones are a 422, not a 400. */
record PositionRequest(
        @Nullable @Schema(nullable = true, example = "-10.00012")
        String lat,

        @Nullable @Schema(nullable = true, example = "-50.00012")
        String lon) {}
