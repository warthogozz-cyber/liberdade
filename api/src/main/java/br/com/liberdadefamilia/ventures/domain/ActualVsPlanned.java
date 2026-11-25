package br.com.liberdadefamilia.ventures.domain;

import br.com.liberdadefamilia.ledger.VentureMonth;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Compares what a project really made with what was planned, and finds the real payback. */
public final class ActualVsPlanned {

    private static final int SHOWN_MONTHS = 12;

    private ActualVsPlanned() {}

    public static ActualVsPlannedResult compute(
            VentureFigures planned,
            @Nullable LocalDate startedOn,
            @Nullable LocalDate closedOn,
            VentureStatus status,
            List<VentureMonth> actual,
            YearMonth currentMonth) {
        YearMonth end = closedOn != null ? YearMonth.from(closedOn) : currentMonth;

        Map<YearMonth, VentureMonth> byMonth = new HashMap<>();
        for (VentureMonth m : actual) {
            if (!m.month().isAfter(end)) {
                byMonth.put(m.month(), m);
            }
        }
        YearMonth realStart =
                byMonth.keySet().stream().min(Comparator.naturalOrder()).orElse(null);
        YearMonth plannedStart =
                status == VentureStatus.PLANNING || startedOn == null ? null : YearMonth.from(startedOn);

        YearMonth start = earliest(realStart, plannedStart);
        List<MonthRow> rows = new ArrayList<>();
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        BigDecimal plannedIncome = null;
        BigDecimal plannedCost = null;
        BigDecimal plannedResult = null;
        if (start != null) {
            for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
                VentureMonth real = byMonth.get(month);
                BigDecimal in = real == null ? BigDecimal.ZERO : real.income();
                BigDecimal out = real == null ? BigDecimal.ZERO : real.expense();
                income = income.add(in);
                expense = expense.add(out);
                boolean inPlan = plannedStart != null && !month.isBefore(plannedStart);
                if (inPlan) {
                    plannedIncome = add(plannedIncome, planned.plannedRevenue());
                    plannedCost = add(plannedCost, planned.plannedCost());
                    plannedResult = add(plannedResult, planned.monthlyProfit());
                }
                rows.add(row(month, in, out, inPlan ? planned : null));
            }
        }

        List<MonthRow> shown = new ArrayList<>(rows.subList(Math.max(0, rows.size() - SHOWN_MONTHS), rows.size()));
        Collections.reverse(shown);
        ActualVsPlannedResult.Totals totals = new ActualVsPlannedResult.Totals(
                money(income),
                money(expense),
                money(income.subtract(expense)),
                moneyOrNull(plannedIncome),
                moneyOrNull(plannedCost),
                moneyOrNull(plannedResult),
                plannedResult == null ? null : compare(income.subtract(expense), plannedResult),
                plannedResult == null
                        ? null
                        : money(income.subtract(expense).subtract(plannedResult).abs()));
        return new ActualVsPlannedResult(shown, totals, realPayback(realStart, end, byMonth));
    }

    private static MonthRow row(YearMonth month, BigDecimal in, BigDecimal out, @Nullable VentureFigures planned) {
        BigDecimal result = money(in.subtract(out));
        if (planned == null) {
            return new MonthRow(month, money(in), money(out), result, null, null, null, null, null);
        }
        BigDecimal plannedResult = money(planned.monthlyProfit());
        Comparison comparison = compare(result, plannedResult);
        return new MonthRow(
                month,
                money(in),
                money(out),
                result,
                money(planned.plannedRevenue()),
                money(planned.plannedCost()),
                plannedResult,
                comparison,
                money(result.subtract(plannedResult).abs()));
    }

    private static Comparison compare(BigDecimal result, BigDecimal plannedResult) {
        int cmp = money(result).compareTo(money(plannedResult));
        return cmp < 0 ? Comparison.BELOW : cmp > 0 ? Comparison.ABOVE : Comparison.EQUAL;
    }

    private static RealPayback realPayback(
            @Nullable YearMonth realStart, YearMonth end, Map<YearMonth, VentureMonth> byMonth) {
        if (realStart == null) {
            return new RealPayback(RealPaybackState.NO_DATA, null, null, null);
        }
        BigDecimal cumulative = BigDecimal.ZERO;
        BigDecimal lowest = BigDecimal.ZERO;
        boolean wasNegative = false;
        Integer paidBackIn = null;
        int index = 0;
        for (YearMonth month = realStart; !month.isAfter(end); month = month.plusMonths(1)) {
            index++;
            VentureMonth real = byMonth.get(month);
            if (real != null) {
                cumulative = cumulative.add(real.income()).subtract(real.expense());
            }
            if (cumulative.compareTo(lowest) < 0) {
                lowest = cumulative;
            }
            if (cumulative.signum() < 0) {
                wasNegative = true;
            } else if (wasNegative && paidBackIn == null) {
                paidBackIn = index;
            }
        }
        if (!wasNegative) {
            return new RealPayback(RealPaybackState.NEVER_NEGATIVE, null, null, null);
        }
        BigDecimal investment = money(lowest.negate());
        if (paidBackIn != null) {
            return new RealPayback(RealPaybackState.PAID_BACK, investment, paidBackIn, null);
        }
        return new RealPayback(RealPaybackState.NOT_YET, investment, null, money(cumulative.negate()));
    }

    private static @Nullable YearMonth earliest(@Nullable YearMonth a, @Nullable YearMonth b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isBefore(b) ? a : b;
    }

    private static BigDecimal add(@Nullable BigDecimal sum, BigDecimal value) {
        return sum == null ? value : sum.add(value);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }

    private static @Nullable BigDecimal moneyOrNull(@Nullable BigDecimal value) {
        return value == null ? null : money(value);
    }
}
