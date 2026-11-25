package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.MarketKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A market of the family's own: a name, a kind and, optionally, a position with at most 5 decimals (both coordinates
 * or neither). Also the body that turns a market that left OpenStreetMap into one of the family's.
 */
record NewMarketRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull MarketKind kind,

        @Nullable @Schema(nullable = true, example = "-10.00012")
        String lat,

        @Nullable @Schema(nullable = true, example = "-50.00012")
        String lon) {}
