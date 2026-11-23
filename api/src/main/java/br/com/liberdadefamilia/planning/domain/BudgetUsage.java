package br.com.liberdadefamilia.planning.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jspecify.annotations.Nullable;

/**
 * How much of a limit was spent. The percent is the floor of {@code spent x 100 / limit}, so 79.99% never reads as
 * 80%. {@code overBy} is set only when the limit was passed.
 */
public record BudgetUsage(
        int percent, BudgetState state, @Nullable BigDecimal overBy) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int NEAR_PERCENT = 80;

    /** The usage of {@code spent} against {@code limit}, which must be positive. */
    public static BudgetUsage of(BigDecimal spent, BigDecimal limit) {
        int percent =
                spent.multiply(HUNDRED).divide(limit, 0, RoundingMode.FLOOR).intValueExact();
        int comparison = spent.compareTo(limit);
        if (comparison > 0) {
            return new BudgetUsage(percent, BudgetState.OVER, spent.subtract(limit));
        }
        if (comparison == 0) {
            return new BudgetUsage(percent, BudgetState.AT, null);
        }
        return new BudgetUsage(percent, percent >= NEAR_PERCENT ? BudgetState.NEAR : BudgetState.UNDER, null);
    }
}
