package br.com.liberdadefamilia.indicators.domain;

import java.math.BigDecimal;

/** The plausible values of an indicator; a point outside it means the source answered something wrong. */
public final class ValueRange {

    private static final BigDecimal RATE_MIN = new BigDecimal("-5");
    private static final BigDecimal RATE_MAX = new BigDecimal("100");
    private static final BigDecimal BRL_MIN = BigDecimal.ONE;
    private static final BigDecimal BRL_MAX = new BigDecimal("50");

    private ValueRange() {}

    /** Rates are accepted from -5 to 100 and the dollar from 1 to 50, limits included. */
    public static boolean accepts(IndicatorUnit unit, BigDecimal value) {
        BigDecimal min = unit == IndicatorUnit.BRL ? BRL_MIN : RATE_MIN;
        BigDecimal max = unit == IndicatorUnit.BRL ? BRL_MAX : RATE_MAX;
        return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }
}
