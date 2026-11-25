package br.com.liberdadefamilia.ventures.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A product the caller sends. A known {@code id} keeps the product; amounts are decimal texts such as "4.50". */
record ProductInput(
        @Nullable @Schema(nullable = true) UUID id,
        String name,

        @Schema(type = "string", pattern = "^[0-9]+([.][0-9]{1,2})?$", example = "1.80")
        BigDecimal unitVariableCost,

        @Schema(type = "string", pattern = "^[0-9]+([.][0-9]{1,2})?$", example = "4.50")
        BigDecimal unitPrice,

        int plannedUnitsMonth) {

    ProductInput {
        if (name != null) {
            name = name.strip();
        }
    }
}
