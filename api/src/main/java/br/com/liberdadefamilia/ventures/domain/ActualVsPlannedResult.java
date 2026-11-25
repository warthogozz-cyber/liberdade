package br.com.liberdadefamilia.ventures.domain;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The last months (newest first), the sums over the whole period and the real payback. */
public record ActualVsPlannedResult(List<MonthRow> months, Totals totals, RealPayback real) {

    public ActualVsPlannedResult {
        months = List.copyOf(months);
    }

    /** Sums over the whole period; the planned sums cover only the months of the planned period. */
    public record Totals(
            BigDecimal income,
            BigDecimal expense,
            BigDecimal result,
            @Nullable BigDecimal plannedIncome,
            @Nullable BigDecimal plannedCost,
            @Nullable BigDecimal plannedResult) {}
}
