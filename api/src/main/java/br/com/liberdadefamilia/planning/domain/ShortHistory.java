package br.com.liberdadefamilia.planning.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.jspecify.annotations.Nullable;

/** Whether the family has too little history for a three-month average. */
public final class ShortHistory {

    private ShortHistory() {}

    /**
     * True when nothing was ever confirmed, when the first confirmed entry is in a month after the oldest of the
     * three months, or when the three-month sum is missing or zero.
     */
    public static boolean of(
            @Nullable LocalDate firstConfirmedOn, @Nullable BigDecimal threeMonthSum, YearMonth current) {
        if (firstConfirmedOn == null) {
            return true;
        }
        if (YearMonth.from(firstConfirmedOn).isAfter(current.minusMonths(3))) {
            return true;
        }
        return threeMonthSum == null || threeMonthSum.signum() == 0;
    }
}
