package br.com.liberdadefamilia.ventures.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** The monthly rate that compounds to a given yearly rate: (1 + a/100)^(1/12) - 1. */
public final class MonthlyRoot {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_EVEN);
    private static final BigDecimal TOLERANCE = new BigDecimal("1E-19");
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal ELEVEN = BigDecimal.valueOf(11);
    private static final int MAX_ROUNDS = 100;

    private MonthlyRoot() {}

    /** Newton on x^12 = 1 + a/100, starting from 1 + a/1200. A zero rate gives an exact zero. */
    public static BigDecimal of(BigDecimal annualPercent) {
        if (annualPercent.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal target = BigDecimal.ONE.add(annualPercent.divide(BigDecimal.valueOf(100), MC), MC);
        BigDecimal x = BigDecimal.ONE.add(annualPercent.divide(BigDecimal.valueOf(1200), MC), MC);
        for (int round = 0; round < MAX_ROUNDS; round++) {
            // x - (x^12 - t) / (12 x^11) is the same as (11 x + t / x^11) / 12
            BigDecimal x11 = x.pow(11, MC);
            BigDecimal next =
                    ELEVEN.multiply(x, MC).add(target.divide(x11, MC), MC).divide(TWELVE, MC);
            boolean done = next.subtract(x, MC).abs().compareTo(TOLERANCE) < 0;
            x = next;
            if (done) {
                break;
            }
        }
        return x.subtract(BigDecimal.ONE, MC);
    }
}
