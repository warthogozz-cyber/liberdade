package br.com.liberdadefamilia.indicators.internal;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Fixed values for dev, e2e and tests: no network, same answer every time. */
public class FakeIndicatorSource implements IndicatorSource {

    private static final int SELIC_CHANGE_DAYS_BEFORE_END = 20;

    @Override
    public List<FetchedPoint> fetch(IndicatorCode code, String sourceRef, LocalDate from, LocalDate to) {
        String url = "fake:" + code;
        boolean daily = code.frequency() == IndicatorCode.Frequency.DAILY;
        List<FetchedPoint> points = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            boolean published = daily ? isWeekday(day) : day.getDayOfMonth() == 1;
            if (published) {
                points.add(new FetchedPoint(day, value(code, day, to), url));
            }
        }
        BigDecimal last = lastValue(code);
        if (last != null && !points.isEmpty()) {
            FetchedPoint tail = points.removeLast();
            points.add(new FetchedPoint(tail.refDate(), last, url));
        }
        return points;
    }

    private static boolean isWeekday(LocalDate day) {
        DayOfWeek d = day.getDayOfWeek();
        return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY;
    }

    private static BigDecimal value(IndicatorCode code, LocalDate day, LocalDate to) {
        return switch (code) {
            case SELIC_TARGET ->
                day.isBefore(to.minusDays(SELIC_CHANGE_DAYS_BEFORE_END))
                        ? new BigDecimal("14.75")
                        : new BigDecimal("15.00");
            case CDI -> new BigDecimal("14.90");
            case IPCA_MONTH -> new BigDecimal("0.40");
            case IPCA_12M -> new BigDecimal("4.10");
            case USD_PTAX_SELL -> new BigDecimal("5.4321");
        };
    }

    /** The value of the newest point when it differs from the rest; null when the series has no such step. */
    private static BigDecimal lastValue(IndicatorCode code) {
        return switch (code) {
            case IPCA_MONTH -> new BigDecimal("0.50");
            case IPCA_12M -> new BigDecimal("4.00");
            case USD_PTAX_SELL -> new BigDecimal("5.4100");
            case SELIC_TARGET, CDI -> null;
        };
    }
}
