package br.com.liberdadefamilia.ventures.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class VentureCalculatorTest {

    private static CostLine cost(String amount) {
        return new CostLine(new BigDecimal(amount));
    }

    private static ProductLine product(String cost, String price, int units) {
        return new ProductLine(new BigDecimal(cost), new BigDecimal(price), units);
    }

    private static VentureFigures calc(List<CostLine> initial, List<CostLine> fixed, ProductLine... products) {
        return VentureCalculator.calculate(initial, fixed, List.of(products));
    }

    private static String s(BigDecimal value) {
        return value.toPlainString();
    }

    @Test
    void prdExample() {
        VentureFigures f = calc(List.of(cost("800.00")), List.of(cost("270.00")), product("1.80", "4.50", 300));

        ProductFigures p = f.products().get(0);
        assertThat(s(p.margin())).isEqualTo("2.70");
        assertThat(s(p.marginPercent())).isEqualTo("60.0");
        assertThat(p.priceCoversCost()).isTrue();
        assertThat(f.breakEven().state()).isEqualTo(BreakEvenState.OK);
        assertThat(p.breakEvenUnits()).isEqualTo(100L);
        assertThat(s(f.breakEven().revenue())).isEqualTo("450.00");
        assertThat(s(f.breakEven().weightedMarginPercent())).isEqualTo("60.0");
        assertThat(s(f.plannedRevenue())).isEqualTo("1350.00");
        assertThat(s(f.plannedCost())).isEqualTo("810.00");
        assertThat(s(f.totalMargin())).isEqualTo("810.00");
        assertThat(s(f.monthlyProfit())).isEqualTo("540.00");
        assertThat(f.paybackState()).isEqualTo(PaybackState.MONTHS);
        assertThat(f.paybackMonths()).isEqualTo(2L);
        assertThat(f.roiState()).isEqualTo(RoiState.OK);
        assertThat(s(f.roi12mPercent())).isEqualTo("710.0");
        assertThat(s(f.initialInvestment())).isEqualTo("800.00");
        assertThat(s(f.fixedMonthly())).isEqualTo("270.00");
    }

    @Test
    void breakEvenRoundsUp() {
        VentureFigures f = calc(List.of(), List.of(cost("100.00")), product("1.00", "4.00", 0));

        assertThat(f.products().get(0).breakEvenUnits()).isEqualTo(34L);
        assertThat(s(f.breakEven().revenue())).isEqualTo("136.00");
    }

    @Test
    void singleProductIgnoresPlannedUnits() {
        VentureFigures f = calc(List.of(cost("800.00")), List.of(cost("270.00")), product("1.80", "4.50", 0));

        assertThat(f.products().get(0).breakEvenUnits()).isEqualTo(100L);
        assertThat(s(f.breakEven().revenue())).isEqualTo("450.00");
        assertThat(s(f.monthlyProfit())).isEqualTo("-270.00");
        assertThat(f.paybackState()).isEqualTo(PaybackState.NEVER);
        assertThat(f.paybackMonths()).isNull();
    }

    @Test
    void twoProductsUseTheMix() {
        VentureFigures f =
                calc(List.of(), List.of(cost("300.00")), product("2.00", "5.00", 100), product("1.00", "2.00", 100));

        assertThat(s(f.totalMargin())).isEqualTo("400.00");
        assertThat(s(f.plannedRevenue())).isEqualTo("700.00");
        assertThat(s(f.breakEven().weightedMarginPercent())).isEqualTo("57.1");
        assertThat(s(f.breakEven().revenue())).isEqualTo("525.00");
        assertThat(f.products().get(0).breakEvenUnits()).isEqualTo(75L);
        assertThat(f.products().get(1).breakEvenUnits()).isEqualTo(75L);
    }

    @Test
    void negativeMarginProductInMix() {
        VentureFigures f =
                calc(List.of(), List.of(cost("100.00")), product("1.00", "3.00", 100), product("5.00", "4.00", 50));

        ProductFigures b = f.products().get(1);
        assertThat(b.priceCoversCost()).isFalse();
        assertThat(s(b.margin())).isEqualTo("-1.00");
        assertThat(s(f.totalMargin())).isEqualTo("150.00");
        assertThat(f.products().get(0).breakEvenUnits()).isEqualTo(67L);
        assertThat(b.breakEvenUnits()).isEqualTo(34L);
    }

    @Test
    void mixWithoutPlannedUnits() {
        VentureFigures f =
                calc(List.of(), List.of(cost("100.00")), product("1.00", "3.00", 0), product("1.00", "2.00", 0));

        assertThat(f.breakEven().state()).isEqualTo(BreakEvenState.NEEDS_PLANNED_UNITS);
        assertThat(f.breakEven().revenue()).isNull();
        assertThat(f.breakEven().weightedMarginPercent()).isNull();
        assertThat(f.products()).allSatisfy(p -> assertThat(p.breakEvenUnits()).isNull());
    }

    @Test
    void totalMarginNotPositive() {
        VentureFigures f =
                calc(List.of(), List.of(cost("100.00")), product("1.00", "2.00", 10), product("5.00", "4.00", 10));

        assertThat(s(f.totalMargin())).isEqualTo("0.00");
        assertThat(f.breakEven().state()).isEqualTo(BreakEvenState.NO_BREAK_EVEN);
        assertThat(f.paybackState()).isEqualTo(PaybackState.NEVER);
    }

    @Test
    void singleProductPriceBelowCost() {
        VentureFigures f = calc(List.of(), List.of(cost("100.00")), product("5.00", "4.00", 10));

        assertThat(f.breakEven().state()).isEqualTo(BreakEvenState.NO_BREAK_EVEN);
        assertThat(f.products().get(0).priceCoversCost()).isFalse();
        assertThat(f.products().get(0).breakEvenUnits()).isNull();
    }

    @Test
    void noProducts() {
        VentureFigures f = calc(List.of(cost("50.00")), List.of(cost("120.00")));

        assertThat(f.breakEven().state()).isEqualTo(BreakEvenState.NO_PRODUCTS);
        assertThat(f.products()).isEmpty();
        assertThat(s(f.monthlyProfit())).isEqualTo("-120.00");
    }

    @Test
    void zeroFixedCosts() {
        VentureFigures f = calc(List.of(), List.of(), product("1.80", "4.50", 300));

        assertThat(f.products().get(0).breakEvenUnits()).isEqualTo(0L);
        assertThat(s(f.breakEven().revenue())).isEqualTo("0.00");
    }

    @Test
    void zeroInvestment() {
        VentureFigures f = calc(List.of(), List.of(cost("270.00")), product("1.80", "4.50", 300));

        assertThat(f.roiState()).isEqualTo(RoiState.NO_INVESTMENT);
        assertThat(f.roi12mPercent()).isNull();
        assertThat(f.paybackMonths()).isEqualTo(1L);
    }

    @Test
    void paybackAtLeastOneMonth() {
        VentureFigures f = calc(List.of(cost("100.00")), List.of(cost("270.00")), product("1.80", "4.50", 300));

        assertThat(s(f.monthlyProfit())).isEqualTo("540.00");
        assertThat(f.paybackMonths()).isEqualTo(1L);
    }

    @Test
    void negativeRoiIsReported() {
        VentureFigures f = calc(List.of(cost("10000.00")), List.of(cost("1250.00")), product("0.00", "4.50", 300));

        assertThat(s(f.monthlyProfit())).isEqualTo("100.00");
        assertThat(s(f.roi12mPercent())).isEqualTo("-88.0");
    }

    @Test
    void roundsHalfEven() {
        VentureFigures a = calc(List.of(cost("0.125")), List.of(cost("0.135")), product("87.75", "100", 1));
        VentureFigures b = calc(List.of(), List.of(), product("87.65", "100", 1));

        assertThat(s(a.products().get(0).marginPercent())).isEqualTo("12.2");
        assertThat(s(b.products().get(0).marginPercent())).isEqualTo("12.4");
        assertThat(s(a.initialInvestment())).isEqualTo("0.12");
        assertThat(s(a.fixedMonthly())).isEqualTo("0.14");
    }

    @Test
    void singleProductZeroMarginDoesNotCoverCost() {
        VentureFigures f = calc(List.of(), List.of(cost("100.00")), product("4.00", "4.00", 10));

        assertThat(f.breakEven().state()).isEqualTo(BreakEvenState.NO_BREAK_EVEN);
        assertThat(f.products().get(0).priceCoversCost()).isFalse();
    }

    @Test
    void mixProductWithZeroMarginDoesNotCoverCost() {
        VentureFigures f =
                calc(List.of(), List.of(cost("100.00")), product("1.00", "3.00", 100), product("4.00", "4.00", 50));

        assertThat(f.products().get(0).priceCoversCost()).isTrue();
        assertThat(f.products().get(1).priceCoversCost()).isFalse();
    }

    @Test
    void mixWithZeroFixedCosts() {
        VentureFigures f = calc(List.of(), List.of(), product("2.00", "5.00", 100), product("1.00", "2.00", 100));

        assertThat(f.breakEven().state()).isEqualTo(BreakEvenState.OK);
        assertThat(s(f.breakEven().revenue())).isEqualTo("0.00");
        assertThat(f.products()).allSatisfy(p -> assertThat(p.breakEvenUnits()).isEqualTo(0L));
    }

    @Test
    void maximumInputsDoNotOverflow() {
        List<CostLine> twenty = java.util.Collections.nCopies(20, cost("999999999.99"));

        VentureFigures payback = calc(twenty, List.of(), product("0.00", "0.01", 1));
        assertThat(s(payback.monthlyProfit())).isEqualTo("0.01");
        assertThat(payback.paybackMonths()).isEqualTo(1_999_999_999_980L);

        VentureFigures mix = calc(List.of(), twenty, product("0.01", "0.02", 1), product("1.00", "1.00", 1_000_000));
        assertThat(s(mix.totalMargin())).isEqualTo("0.01");
        assertThat(mix.products().get(0).breakEvenUnits()).isEqualTo(1_999_999_999_980L);
        assertThat(mix.products().get(1).breakEvenUnits()).isEqualTo(1_999_999_999_980_000_000L);
    }
}
