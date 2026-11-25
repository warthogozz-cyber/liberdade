package br.com.liberdadefamilia.ventures.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A cost line the caller sends. A known {@code id} keeps the line; the amount is a decimal text such as "12.50". */
record CostItemInput(
        @Nullable @Schema(nullable = true) UUID id,
        String name,

        @Schema(type = "string", pattern = "^[0-9]+([.][0-9]{1,2})?$", example = "12.50")
        BigDecimal amount) {

    CostItemInput {
        if (name != null) {
            name = name.strip();
        }
    }
}
