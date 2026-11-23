package br.com.liberdadefamilia.billing.domain;

import java.time.Instant;
import java.time.ZoneId;

/**
 * The seven days in which a family may take its payment back. It opens at the payment and closes at the end of the
 * seventh day after it, in São Paulo time: the day of payment does not count and the last day counts whole (CC art.
 * 132). So the window is never shorter than 7 × 24 h, and the date the Plano screen shows is the last day allowed.
 */
public final class RefundWindow {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final int DAYS = 7;

    private RefundWindow() {}

    /** The first instant the window is closed: 00:00 in São Paulo on the eighth day after the payment day. */
    public static Instant deadline(Instant paidAt) {
        return paidAt.atZone(SAO_PAULO)
                .toLocalDate()
                .plusDays(DAYS + 1L)
                .atStartOfDay(SAO_PAULO)
                .toInstant();
    }

    /** Open from the payment on, closed at the deadline itself. */
    public static boolean isOpen(Instant paidAt, Instant now) {
        return !now.isBefore(paidAt) && now.isBefore(deadline(paidAt));
    }
}
