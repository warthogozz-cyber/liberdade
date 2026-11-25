package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.ventures.domain.PaybackState;
import br.com.liberdadefamilia.ventures.domain.VentureStatus;
import br.com.liberdadefamilia.ventures.domain.VentureType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A project in the list. {@code hasExamples} is true while any line still holds the model's value. */
record VentureSummary(
        UUID id,
        String name,
        VentureType type,
        VentureStatus status,
        @Nullable @Schema(nullable = true) LocalDate startedOn,
        @Nullable @Schema(nullable = true) LocalDate closedOn,
        Money monthlyProfit,
        PaybackState paybackState,

        @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Long paybackMonths,

        boolean hasExamples,
        long version) {}
