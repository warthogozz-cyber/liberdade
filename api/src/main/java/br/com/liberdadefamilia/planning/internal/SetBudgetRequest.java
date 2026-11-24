package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/** The limit of a category for a month. {@code version} is null to create it, and the line's version to change it. */
record SetBudgetRequest(
        @NotNull Money limitAmount,
        @Nullable @Schema(nullable = true) Long version) {}
