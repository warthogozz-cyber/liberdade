package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.planning.domain.BudgetState;
import br.com.liberdadefamilia.shared.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A category's limit for the month and how much of it was spent. {@code overBy} is set only past the limit. */
record BudgetLineView(
        UUID categoryId,
        Money limit,
        Money spent,
        int usagePercent,
        BudgetState state,

        @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Money overBy,

        long version) {}
