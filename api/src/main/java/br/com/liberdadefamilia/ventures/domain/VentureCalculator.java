package br.com.liberdadefamilia.ventures.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** The figures of a project: totals, break-even, payback and the 12 month return. */
public final class VentureCalculator {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_EVEN);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private VentureCalculator() {}

    public static VentureFigures calculate(List<CostLine> initial, List<CostLine> fixed, List<ProductLine> products) {
        BigDecimal initialInvestment = sum(initial);
        BigDecimal fixedMonthly = sum(fixed);

        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal variableCost = BigDecimal.ZERO;
        BigDecimal totalMargin = BigDecimal.ZERO;
        int plannedTotal = 0;
        for (ProductLine p : products) {
            BigDecimal planned = BigDecimal.valueOf(p.plannedUnitsMonth());
            revenue = revenue.add(planned.multiply(p.unitPrice()));
            variableCost = variableCost.add(planned.multiply(p.unitVariableCost()));
            totalMargin = totalMargin.add(planned.multiply(margin(p)));
            plannedTotal += p.plannedUnitsMonth();
        }
        BigDecimal plannedCost = variableCost.add(fixedMonthly);
        BigDecimal profit = revenue.subtract(plannedCost);

        BreakEvenState state;
        BigDecimal breakEvenRevenue = null;
        BigDecimal weighted = null;
        Long[] breakEvenUnits = new Long[products.size()];
        if (products.isEmpty()) {
            state = BreakEvenState.NO_PRODUCTS;
        } else if (products.size() == 1) {
            ProductLine p = products.get(0);
            BigDecimal margin = margin(p);
            if (margin.signum() <= 0) {
                state = BreakEvenState.NO_BREAK_EVEN;
            } else {
                state = BreakEvenState.OK;
                BigDecimal needed = fixedMonthly.divide(margin, 0, RoundingMode.CEILING);
                breakEvenUnits[0] = needed.longValueExact();
                breakEvenRevenue = needed.multiply(p.unitPrice());
                weighted = percent(margin, p.unitPrice());
            }
        } else if (plannedTotal == 0) {
            state = BreakEvenState.NEEDS_PLANNED_UNITS;
        } else if (totalMargin.signum() <= 0) {
            state = BreakEvenState.NO_BREAK_EVEN;
        } else {
            state = BreakEvenState.OK;
            breakEvenRevenue = fixedMonthly.multiply(revenue).divide(totalMargin, MC);
            weighted = percent(totalMargin, revenue);
            for (int i = 0; i < breakEvenUnits.length; i++) {
                breakEvenUnits[i] = BigDecimal.valueOf(products.get(i).plannedUnitsMonth())
                        .multiply(fixedMonthly)
                        .divide(totalMargin, 0, RoundingMode.CEILING)
                        .longValueExact();
            }
        }

        List<ProductFigures> productFigures = new ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            ProductLine p = products.get(i);
            BigDecimal margin = margin(p);
            productFigures.add(new ProductFigures(
                    money(margin), percent1(percent(margin, p.unitPrice())), margin.signum() > 0, breakEvenUnits[i]));
        }

        Long payback = null;
        PaybackState paybackState = PaybackState.NEVER;
        if (profit.signum() > 0) {
            paybackState = PaybackState.MONTHS;
            payback = Math.max(
                    1, initialInvestment.divide(profit, 0, RoundingMode.CEILING).longValueExact());
        }

        BigDecimal roi = null;
        RoiState roiState = RoiState.NO_INVESTMENT;
        if (initialInvestment.signum() != 0) {
            roiState = RoiState.OK;
            roi = percent1(TWELVE.multiply(profit)
                    .subtract(initialInvestment)
                    .divide(initialInvestment, MC)
                    .multiply(HUNDRED));
        }

        BreakEven breakEven = new BreakEven(
                state,
                breakEvenRevenue == null ? null : money(breakEvenRevenue),
                weighted == null ? null : percent1(weighted));
        return new VentureFigures(
                money(initialInvestment),
                money(fixedMonthly),
                money(revenue),
                money(plannedCost),
                money(totalMargin),
                money(profit),
                roi,
                roiState,
                payback,
                paybackState,
                breakEven,
                productFigures);
    }

    private static BigDecimal sum(List<CostLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (CostLine line : lines) {
            total = total.add(line.amount());
        }
        return total;
    }

    private static BigDecimal margin(ProductLine p) {
        return p.unitPrice().subtract(p.unitVariableCost());
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return part.divide(whole, MC).multiply(HUNDRED);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal percent1(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_EVEN);
    }
}
