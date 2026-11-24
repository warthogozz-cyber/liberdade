package br.com.liberdadefamilia.indicators.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** Counts Monday to Friday days. There is no holiday calendar. */
public final class BusinessDays {

    private BusinessDays() {}

    /** The weekdays in the half-open interval (from, to]; 0 when {@code to} is not after {@code from}. */
    public static int weekdaysAfter(LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate day = from.plusDays(1); !day.isAfter(to); day = day.plusDays(1)) {
            DayOfWeek dow = day.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
    }
}
