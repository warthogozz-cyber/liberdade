package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.ventures.domain.BreakEven;
import br.com.liberdadefamilia.ventures.domain.PaybackState;
import br.com.liberdadefamilia.ventures.domain.ProductFigures;
import br.com.liberdadefamilia.ventures.domain.RoiState;
import br.com.liberdadefamilia.ventures.domain.VentureFigures;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The figures of a project as the API answers them (D9): money as {@link Money}, percentages as text with 1 decimal,
 * and no number where the state says there is none.
 */
record FiguresView(
        Money initialInvestment,
        Money fixedMonthly,
        Money plannedRevenue,
        Money plannedCost,
        Money totalMargin,
        Money monthlyProfit,

        @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "710.0")
        String roi12mPercent,

        RoiState roiState,

        @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Long paybackMonths,

        PaybackState paybackState,
        BreakEvenView breakEven,
        List<ProductFiguresView> products) {

    static FiguresView of(VentureFigures figures, Currency currency) {
        BreakEven breakEven = figures.breakEven();
        return new FiguresView(
                Money.of(figures.initialInvestment(), currency),
                Money.of(figures.fixedMonthly(), currency),
                Money.of(figures.plannedRevenue(), currency),
                Money.of(figures.plannedCost(), currency),
                Money.of(figures.totalMargin(), currency),
                Money.of(figures.monthlyProfit(), currency),
                text(figures.roi12mPercent()),
                figures.roiState(),
                figures.paybackMonths(),
                figures.paybackState(),
                new BreakEvenView(
                        breakEven.state(),
                        breakEven.revenue() == null ? null : Money.of(breakEven.revenue(), currency),
                        text(breakEven.weightedMarginPercent())),
                figures.products().stream()
                        .map(product -> productOf(product, currency))
                        .toList());
    }

    private static ProductFiguresView productOf(ProductFigures product, Currency currency) {
        return new ProductFiguresView(
                Money.of(product.margin(), currency),
                product.marginPercent().toPlainString(),
                product.priceCoversCost(),
                product.breakEvenUnits());
    }

    private static @Nullable String text(@Nullable BigDecimal percent) {
        return percent == null ? null : percent.toPlainString();
    }
}
