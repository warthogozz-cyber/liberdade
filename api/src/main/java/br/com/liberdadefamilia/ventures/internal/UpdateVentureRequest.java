package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.ventures.domain.VentureType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Replaces the project's name, type, start day and all of its items and products at once: what does not come is
 * removed, and the order of each list is the position. {@code version} is the one the caller read.
 */
record UpdateVentureRequest(
        @NotNull Long version,
        @NotNull String name,
        @NotNull VentureType type,
        @Nullable @Schema(nullable = true) LocalDate startedOn,
        @NotNull List<CostItemInput> initialItems,
        @NotNull List<CostItemInput> fixedItems,
        @NotNull List<ProductInput> products) {

    UpdateVentureRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
