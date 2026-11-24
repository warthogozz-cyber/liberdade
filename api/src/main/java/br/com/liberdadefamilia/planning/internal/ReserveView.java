package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * The emergency reserve. {@code months} is how many months of cost the balance covers, rounded down to one decimal;
 * {@code missing} is what is left to reach {@code targetMonths}. Both are null while {@code costSource} is
 * {@code NONE}: the family has too little history and typed no monthly cost.
 */
record ReserveView(
        Money balance,
        boolean hasReserveAccounts,
        @Nullable @Schema(nullable = true) Money averageMonthlyCost,
        CostSource costSource,
        @Nullable @Schema(nullable = true) BigDecimal months,
        int targetMonths,
        @Nullable @Schema(nullable = true) Money missing,
        @Nullable @Schema(nullable = true) Money manualMonthlyCost) {}
