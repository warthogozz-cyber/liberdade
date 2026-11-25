package br.com.liberdadefamilia.ventures.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/** Compounds an initial amount and a monthly contribution under each yearly rate. */
public final class InvestmentSimulator {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_EVEN);

    private InvestmentSimulator() {}

    public static List<InvestmentOutcome> simulate(
            BigDecimal initial, BigDecimal monthly, int years, List<InvestmentScenario> scenarios) {
        int months = years * 12;
        BigDecimal contributed = initial.add(monthly.multiply(BigDecimal.valueOf(months), MC), MC);
        return scenarios.stream()
                .map(scenario -> {
                    BigDecimal growth = BigDecimal.ONE.add(MonthlyRoot.of(scenario.annualPercent()), MC);
                    BigDecimal balance = initial;
                    for (int month = 0; month < months; month++) {
                        balance = balance.multiply(growth, MC).add(monthly, MC);
                    }
                    return new InvestmentOutcome(
                            scenario.name(),
                            money(balance),
                            money(contributed),
                            money(balance.subtract(contributed, MC)));
                })
                .toList();
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }
}
