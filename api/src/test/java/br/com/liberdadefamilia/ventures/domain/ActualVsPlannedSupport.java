package br.com.liberdadefamilia.ventures.domain;

import br.com.liberdadefamilia.ledger.VentureMonth;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Shared fixtures for the actual-vs-planned tests. */
final class ActualVsPlannedSupport {

    static final LocalDate STARTED = LocalDate.of(2026, 9, 1);
    static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    private ActualVsPlannedSupport() {}

    /** The 9.1 prdExample: revenue 1350.00, cost 810.00, profit 540.00 a month. */
    static VentureFigures planned() {
        return new VentureFigures(
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("1350.00"),
                new BigDecimal("810.00"),
                new BigDecimal("540.00"),
                new BigDecimal("540.00"),
                null,
                RoiState.NO_INVESTMENT,
                null,
                PaybackState.NEVER,
                new BreakEven(BreakEvenState.NO_PRODUCTS, null, null),
                List.of());
    }

    static VentureMonth month(String yearMonth, String income, String expense) {
        return new VentureMonth(YearMonth.parse(yearMonth), new BigDecimal(income), new BigDecimal(expense));
    }

    static ActualVsPlannedResult compute(
            LocalDate startedOn, LocalDate closedOn, VentureStatus status, YearMonth current, VentureMonth... actual) {
        return ActualVsPlanned.compute(planned(), startedOn, closedOn, status, List.of(actual), current);
    }

    /** Active since September 2026, now in November 2026. */
    static ActualVsPlannedResult active(VentureMonth... actual) {
        return compute(STARTED, null, VentureStatus.ACTIVE, NOVEMBER, actual);
    }
}
