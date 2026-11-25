package br.com.liberdadefamilia.ventures.domain;

import static br.com.liberdadefamilia.ventures.domain.ActualVsPlannedSupport.NOVEMBER;
import static br.com.liberdadefamilia.ventures.domain.ActualVsPlannedSupport.STARTED;
import static br.com.liberdadefamilia.ventures.domain.ActualVsPlannedSupport.active;
import static br.com.liberdadefamilia.ventures.domain.ActualVsPlannedSupport.compute;
import static br.com.liberdadefamilia.ventures.domain.ActualVsPlannedSupport.month;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.ledger.VentureMonth;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActualVsPlannedTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    void purchasesBeforeStart() {
        ActualVsPlannedResult result = compute(
                LocalDate.of(2026, 10, 5),
                null,
                VentureStatus.ACTIVE,
                NOVEMBER,
                month("2026-09", "0", "800"),
                month("2026-10", "300", "0"));

        // newest first: nov, oct, sep
        assertThat(result.months())
                .extracting(MonthRow::month)
                .containsExactly(YearMonth.of(2026, 11), YearMonth.of(2026, 10), YearMonth.of(2026, 9));
        MonthRow september = result.months().get(2);
        assertThat(september.expense()).isEqualTo(d("800.00"));
        assertThat(september.plannedIncome()).isNull();
        assertThat(september.plannedCost()).isNull();
        assertThat(september.plannedResult()).isNull();
        assertThat(september.comparison()).isNull();
        assertThat(september.difference()).isNull();
        assertThat(result.months().get(1).plannedResult()).isEqualTo(d("540.00"));
        assertThat(result.totals().plannedResult()).isEqualTo(d("1080.00"));
        assertThat(result.real().state()).isEqualTo(RealPaybackState.NOT_YET);
        assertThat(result.real().investment()).isEqualTo(d("800.00"));
    }

    @Test
    void planningHasNoPlanned() {
        ActualVsPlannedResult result = compute(
                null, null, VentureStatus.PLANNING, NOVEMBER, month("2026-10", "0", "50"), month("2026-11", "20", "0"));

        assertThat(result.months()).hasSize(2);
        assertThat(result.months()).allSatisfy(row -> {
            assertThat(row.plannedIncome()).isNull();
            assertThat(row.plannedCost()).isNull();
            assertThat(row.plannedResult()).isNull();
            assertThat(row.comparison()).isNull();
        });
        assertThat(result.months().get(0).income()).isEqualTo(d("20.00"));
        assertThat(result.totals().plannedIncome()).isNull();
        assertThat(result.totals().result()).isEqualTo(d("-30.00"));
    }

    @Test
    void closedStopsAtClosedMonth() {
        ActualVsPlannedResult result = compute(
                STARTED,
                LocalDate.of(2026, 10, 20),
                VentureStatus.CLOSED,
                NOVEMBER,
                month("2026-09", "0", "100"),
                month("2026-11", "500", "0"));

        assertThat(result.months())
                .extracting(MonthRow::month)
                .containsExactly(YearMonth.of(2026, 10), YearMonth.of(2026, 9));
        assertThat(result.totals().income()).isEqualTo(d("0.00"));
        assertThat(result.real().state()).isEqualTo(RealPaybackState.NOT_YET);
        assertThat(result.real().missing()).isEqualTo(d("100.00"));
    }

    @Test
    void reopenedRunsToCurrentMonth() {
        ActualVsPlannedResult result = compute(
                STARTED,
                null,
                VentureStatus.PAUSED,
                NOVEMBER,
                month("2026-09", "0", "100"),
                month("2026-11", "500", "0"));

        assertThat(result.months().get(0).month()).isEqualTo(NOVEMBER);
        assertThat(result.months().get(0).income()).isEqualTo(d("500.00"));
        assertThat(result.real().state()).isEqualTo(RealPaybackState.PAID_BACK);
        assertThat(result.real().months()).isEqualTo(3);
    }

    @Test
    void startedOnCorrected() {
        VentureMonth[] real = {month("2026-09", "0", "100"), month("2026-10", "300", "0")};

        ActualVsPlannedResult october = compute(LocalDate.of(2026, 10, 1), null, VentureStatus.ACTIVE, NOVEMBER, real);
        ActualVsPlannedResult september = compute(STARTED, null, VentureStatus.ACTIVE, NOVEMBER, real);

        assertThat(october.months().get(2).plannedResult()).isNull();
        assertThat(september.months().get(2).plannedResult()).isEqualTo(d("540.00"));
        assertThat(october.real()).isEqualTo(september.real());
        assertThat(october.months())
                .extracting(MonthRow::income)
                .isEqualTo(september.months().stream().map(MonthRow::income).toList());
    }

    @Test
    void lastTwelveMonthsNewestFirst() {
        List<VentureMonth> all = new ArrayList<>();
        YearMonth first = YearMonth.of(2025, 9);
        for (int i = 0; i < 15; i++) {
            all.add(new VentureMonth(first.plusMonths(i), d("100"), d("40")));
        }

        ActualVsPlannedResult result = ActualVsPlanned.compute(
                ActualVsPlannedSupport.planned(), LocalDate.of(2025, 9, 1), null, VentureStatus.ACTIVE, all, NOVEMBER);

        assertThat(result.months()).hasSize(12);
        assertThat(result.months().get(0).month()).isEqualTo(NOVEMBER);
        assertThat(result.months().get(11).month()).isEqualTo(YearMonth.of(2025, 12));
        assertThat(result.totals().income()).isEqualTo(d("1500.00"));
        assertThat(result.totals().expense()).isEqualTo(d("600.00"));
        assertThat(result.totals().result()).isEqualTo(d("900.00"));
        assertThat(result.totals().plannedIncome()).isEqualTo(d("20250.00"));
        assertThat(result.totals().plannedCost()).isEqualTo(d("12150.00"));
        assertThat(result.totals().plannedResult()).isEqualTo(d("8100.00"));
    }

    @Test
    void comparisonIsNeutralEnum() {
        VentureMonth[] real = {month("2026-11", "400", "0")};
        VentureFigures base = ActualVsPlannedSupport.planned();

        assertThat(active(real).months().get(0).comparison()).isEqualTo(Comparison.BELOW);
        assertThat(active(month("2026-11", "540", "0")).months().get(0).comparison())
                .isEqualTo(Comparison.EQUAL);
        assertThat(active(month("2026-11", "600", "0")).months().get(0).comparison())
                .isEqualTo(Comparison.ABOVE);
        assertThat(base.monthlyProfit()).isEqualTo(d("540.00"));
    }

    @Test
    void differenceIsTheNonNegativeGap() {
        MonthRow below = active(month("2026-11", "400", "0")).months().get(0);
        MonthRow equal = active(month("2026-11", "540", "0")).months().get(0);
        MonthRow above = active(month("2026-11", "600.55", "0")).months().get(0);

        assertThat(below.difference()).isEqualTo(d("140.00"));
        assertThat(equal.difference()).isEqualTo(d("0.00"));
        assertThat(above.difference()).isEqualTo(d("60.55"));

        ActualVsPlannedResult.Totals totals =
                active(month("2026-11", "400", "0")).totals();
        // September to November: three planned months of 540.00
        assertThat(totals.difference()).isEqualTo(d("1220.00"));
        assertThat(totals.comparison()).isEqualTo(Comparison.BELOW);
        assertThat(active(month("2026-11", "2000", "0")).totals().comparison()).isEqualTo(Comparison.ABOVE);
    }

    @Test
    void noPlannedGivesNoDifferenceInTotals() {
        ActualVsPlannedResult result =
                compute(LocalDate.of(2026, 10, 5), null, VentureStatus.PLANNING, NOVEMBER, month("2026-09", "0", "30"));

        assertThat(result.totals().difference()).isNull();
        assertThat(result.totals().comparison()).isNull();
    }

    @Test
    void futureTransactionsOutsideTheEnd() {
        ActualVsPlannedResult result = active(month("2026-10", "100", "0"), month("2026-12", "999", "0"));

        assertThat(result.months().get(0).month()).isEqualTo(NOVEMBER);
        assertThat(result.totals().income()).isEqualTo(d("100.00"));
        assertThat(result.real().state()).isEqualTo(RealPaybackState.NEVER_NEGATIVE);
    }

    @Test
    void noStartAndNoRealGivesNothing() {
        ActualVsPlannedResult result = compute(null, null, VentureStatus.PLANNING, NOVEMBER);

        assertThat(result.months()).isEmpty();
        assertThat(result.totals().income()).isEqualTo(d("0.00"));
        assertThat(result.totals().plannedIncome()).isNull();
        assertThat(result.real().state()).isEqualTo(RealPaybackState.NO_DATA);
    }

    @Test
    void roundsOnlyTheOutput() {
        ActualVsPlannedResult result = active(month("2026-11", "0.005", "0.0005"));

        assertThat(result.months().get(0).income()).isEqualTo(d("0.00"));
        assertThat(result.totals().result()).isEqualTo(d("0.00"));
    }
}
