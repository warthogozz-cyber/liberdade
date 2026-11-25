package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.ventures.domain.VentureStatus;
import br.com.liberdadefamilia.ventures.domain.VentureType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A project with its lines, steps and figures. {@code version} is the one a change must carry back. */
record VentureView(
        UUID id,
        String name,
        VentureType type,
        @Nullable @Schema(nullable = true) String templateKey,
        VentureStatus status,
        @Nullable @Schema(nullable = true) LocalDate startedOn,
        @Nullable @Schema(nullable = true) LocalDate closedOn,
        String currency,
        List<CostItemView> initialItems,
        List<CostItemView> fixedItems,
        List<ProductView> products,
        List<StepView> steps,
        FiguresView figures,
        long version) {}
