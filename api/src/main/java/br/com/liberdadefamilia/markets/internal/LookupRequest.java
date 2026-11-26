package br.com.liberdadefamilia.markets.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * The key of a receipt as the QR or the person gave it. {@code version} is the QR's version (1, 2 or 3), or 0 for a key
 * typed or pasted without a QR. {@code p} is the QR's {@code p} parameter: version 2 sends it whole, version 3 sends
 * {@code <key>|3|1}, and versions 1 and 0 send none.
 */
record LookupRequest(
        @NotNull @Size(max = 100) @Schema(description = "The 44 positions; spaces and dots are ignored")
        String chave,

        @Schema(allowableValues = {"0", "1", "2", "3"}) int version,
        @Nullable @Size(max = 1000) @Schema(nullable = true) String p) {}
