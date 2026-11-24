package br.com.liberdadefamilia.indicators.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** How the last value of an indicator moved, from its points in ascending date order. */
public final class Variations {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Variations() {}

    /**
     * The last time the value changed: the last value minus the different value before it, in percentage points,
     * counting from the first day of the last value. Null when the points never changed.
     */
    public static @Nullable Variation lastChange(List<Point> ascending) {
        if (ascending.size() < 2) {
            return null;
        }
        Point last = ascending.getLast();
        for (int i = ascending.size() - 2; i >= 0; i--) {
            Point before = ascending.get(i);
            if (before.value().compareTo(last.value()) != 0) {
                Point firstOfNew = ascending.get(i + 1);
                return new Variation(
                        pp(last, before), VariationUnit.PP, VariationBasis.LAST_CHANGE, firstOfNew.refDate());
            }
        }
        return null;
    }

    /**
     * The last value against the previous point, in percentage points or as a percent change. The date it counts
     * from is the previous point's. Null with fewer than two points.
     */
    public static @Nullable Variation vsPrevious(List<Point> ascending, VariationUnit unit) {
        if (ascending.size() < 2) {
            return null;
        }
        Point last = ascending.getLast();
        Point previous = ascending.get(ascending.size() - 2);
        BigDecimal value = unit == VariationUnit.PP ? pp(last, previous) : percent(last, previous);
        return new Variation(value, unit, VariationBasis.PREVIOUS, previous.refDate());
    }

    private static BigDecimal pp(Point last, Point before) {
        return last.value().subtract(before.value()).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(Point last, Point previous) {
        return last.value()
                .divide(previous.value(), MathContext.DECIMAL64)
                .subtract(BigDecimal.ONE)
                .multiply(HUNDRED)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }
}
