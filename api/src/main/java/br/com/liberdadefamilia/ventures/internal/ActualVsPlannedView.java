package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.ventures.domain.ActualVsPlannedResult;
import br.com.liberdadefamilia.ventures.domain.Comparison;
import br.com.liberdadefamilia.ventures.domain.MonthRow;
import br.com.liberdadefamilia.ventures.domain.PaybackState;
import br.com.liberdadefamilia.ventures.domain.RealPayback;
import br.com.liberdadefamilia.ventures.domain.RealPaybackState;
import br.com.liberdadefamilia.ventures.domain.VentureFigures;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Currency;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What really happened in a project against what it planned (D4): money as {@link Money}, the last months newest
 * first, the sums over the whole period and the real payback. A planned figure is absent outside the planned period.
 */
record ActualVsPlannedView(
        List<MonthView> months, TotalsView totals, PlannedView planned, RealView real, String currency) {

    record MonthView(
            YearMonth month,
            Money income,
            Money expense,
            Money result,

            @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Money plannedIncome,

            @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Money plannedCost,

            @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Money plannedResult,

            @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Comparison comparison,

            @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Money difference) {}

    record TotalsView(
            Money income,
            Money expense,
            Money result,

            @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Money plannedIncome,

            @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Money plannedCost,

            @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Money plannedResult,

            @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Comparison comparison,

            @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Money difference) {}

    record PlannedView(
            Money initialInvestment,

            @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Long paybackMonths,

            PaybackState paybackState) {}

    record RealView(
            RealPaybackState state,

            @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Money investment,

            @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer months,

            @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Money missing) {}

    static ActualVsPlannedView of(ActualVsPlannedResult result, VentureFigures planned, Currency currency) {
        ActualVsPlannedResult.Totals totals = result.totals();
        RealPayback real = result.real();
        return new ActualVsPlannedView(
                result.months().stream().map(row -> monthOf(row, currency)).toList(),
                new TotalsView(
                        money(totals.income(), currency),
                        money(totals.expense(), currency),
                        money(totals.result(), currency),
                        money(totals.plannedIncome(), currency),
                        money(totals.plannedCost(), currency),
                        money(totals.plannedResult(), currency),
                        totals.comparison(),
                        money(totals.difference(), currency)),
                new PlannedView(
                        Money.of(planned.initialInvestment(), currency),
                        planned.paybackMonths(),
                        planned.paybackState()),
                new RealView(
                        real.state(),
                        money(real.investment(), currency),
                        real.months(),
                        money(real.missing(), currency)),
                currency.getCurrencyCode());
    }

    private static MonthView monthOf(MonthRow row, Currency currency) {
        return new MonthView(
                row.month(),
                Money.of(row.income(), currency),
                Money.of(row.expense(), currency),
                Money.of(row.result(), currency),
                money(row.plannedIncome(), currency),
                money(row.plannedCost(), currency),
                money(row.plannedResult(), currency),
                row.comparison(),
                money(row.difference(), currency));
    }

    private static @Nullable Money money(@Nullable BigDecimal amount, Currency currency) {
        return amount == null ? null : Money.of(amount, currency);
    }
}
