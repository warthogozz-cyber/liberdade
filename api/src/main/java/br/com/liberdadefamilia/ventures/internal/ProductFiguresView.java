package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.shared.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** What one product gives: margin per unit, margin percent as text with 1 decimal, and its break-even units. */
record ProductFiguresView(
        Money margin,

        @Schema(example = "60.0") String marginPercent,

        boolean priceCoversCost,

        @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Long breakEvenUnits) {}
