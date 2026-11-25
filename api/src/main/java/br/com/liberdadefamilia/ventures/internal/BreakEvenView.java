package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.ventures.domain.BreakEvenState;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** The break-even point; the numbers are there only when the state is OK. The percent is a text with 1 decimal. */
record BreakEvenView(
        BreakEvenState state,

        @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Money revenue,

        @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "57.1")
        String weightedMarginPercent) {}
