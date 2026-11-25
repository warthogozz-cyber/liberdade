package br.com.liberdadefamilia.ventures.domain;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The figures of a project, already rounded for the response. */
public record VentureFigures(
        BigDecimal initialInvestment,
        BigDecimal fixedMonthly,
        BigDecimal plannedRevenue,
        BigDecimal plannedCost,
        BigDecimal totalMargin,
        BigDecimal monthlyProfit,
        @Nullable BigDecimal roi12mPercent,
        RoiState roiState,
        @Nullable Long paybackMonths,
        PaybackState paybackState,
        BreakEven breakEven,
        List<ProductFigures> products) {

    public VentureFigures {
        products = List.copyOf(products);
    }
}
