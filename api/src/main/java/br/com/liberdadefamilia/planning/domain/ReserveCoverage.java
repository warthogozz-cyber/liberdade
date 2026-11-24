package br.com.liberdadefamilia.planning.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jspecify.annotations.Nullable;

/** How many months of cost the reserve covers, and what is missing to reach the target. */
public record ReserveCoverage(
        @Nullable BigDecimal months,
        @Nullable BigDecimal missing,
        @Nullable BigDecimal average) {

    private static final BigDecimal THREE = BigDecimal.valueOf(3);

    /**
     * Without a positive three-month sum every field is null. Otherwise {@code months} is
     * {@code balance x 3 / sum} floored to one decimal, {@code missing} is {@code max(0, target x sum / 3 - balance)}
     * and {@code average} is {@code sum / 3}, both rounded half up to the cent.
     */
    public static ReserveCoverage of(BigDecimal balance, @Nullable BigDecimal threeMonthSum, int targetMonths) {
        if (threeMonthSum == null || threeMonthSum.signum() <= 0) {
            return new ReserveCoverage(null, null, null);
        }
        BigDecimal months = balance.multiply(THREE).divide(threeMonthSum, 1, RoundingMode.FLOOR);
        BigDecimal needed =
                BigDecimal.valueOf(targetMonths).multiply(threeMonthSum).divide(THREE, 2, RoundingMode.HALF_UP);
        BigDecimal missing = needed.subtract(balance).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal average = threeMonthSum.divide(THREE, 2, RoundingMode.HALF_UP);
        return new ReserveCoverage(months, missing, average);
    }
}
