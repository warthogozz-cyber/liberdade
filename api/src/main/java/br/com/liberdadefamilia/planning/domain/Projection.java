package br.com.liberdadefamilia.planning.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The balance at the end of the current month and of the next three. */
public final class Projection {

    private static final int POINTS = 4;
    private static final BigDecimal THREE = BigDecimal.valueOf(3);

    private Projection() {}

    /**
     * Each point is {@code balanceToday + scheduled - estimatedSpending}. Scheduled sums the flows dated up to the
     * month end, so overdue ones land in the first point. The estimate is the three-month sum spread over the days
     * left in the current month, then a third of it per following month, each part rounded half up to the cent. A
     * missing sum means no estimate.
     */
    public static List<ProjectionPoint> compute(
            LocalDate today, BigDecimal balanceToday, List<Flow> flows, @Nullable BigDecimal threeMonthSum) {
        BigDecimal sum = threeMonthSum == null ? BigDecimal.ZERO : threeMonthSum;
        YearMonth current = YearMonth.from(today);
        BigDecimal estimated = BigDecimal.ZERO.setScale(2);
        List<ProjectionPoint> points = new ArrayList<>(POINTS);
        for (int i = 0; i < POINTS; i++) {
            LocalDate monthEnd = current.plusMonths(i).atEndOfMonth();
            BigDecimal part = i == 0 ? firstMonthPart(sum, today) : sum.divide(THREE, 2, RoundingMode.HALF_UP);
            estimated = estimated.add(part);
            BigDecimal scheduled = BigDecimal.ZERO.setScale(2);
            for (Flow flow : flows) {
                if (!flow.on().isAfter(monthEnd)) {
                    scheduled = scheduled.add(flow.signedAmount());
                }
            }
            scheduled = scheduled.setScale(2, RoundingMode.HALF_UP);
            BigDecimal balance = balanceToday.add(scheduled).subtract(estimated).setScale(2, RoundingMode.HALF_UP);
            points.add(new ProjectionPoint(monthEnd, balance, scheduled, estimated));
        }
        return List.copyOf(points);
    }

    private static BigDecimal firstMonthPart(BigDecimal sum, LocalDate today) {
        int length = today.lengthOfMonth();
        return sum.multiply(BigDecimal.valueOf(length - today.getDayOfMonth()))
                .divide(BigDecimal.valueOf(3L * length), 2, RoundingMode.HALF_UP);
    }
}
