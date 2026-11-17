package br.com.liberdadefamilia.billing.domain;

import java.time.Duration;
import java.time.Instant;

/** The seven days in which a family may take its payment back. It opens at the payment and closes at the deadline. */
public final class RefundWindow {

    private static final Duration LENGTH = Duration.ofDays(7);

    private RefundWindow() {}

    public static Instant deadline(Instant paidAt) {
        return paidAt.plus(LENGTH);
    }

    /** Open from the payment on, closed at the deadline itself. */
    public static boolean isOpen(Instant paidAt, Instant now) {
        return !now.isBefore(paidAt) && now.isBefore(deadline(paidAt));
    }
}
