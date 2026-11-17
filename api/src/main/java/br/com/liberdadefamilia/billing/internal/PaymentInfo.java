package br.com.liberdadefamilia.billing.internal;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A payment as the gateway reports it.
 *
 * @param paidAt the start of the payment day (the gateway only gives a date); null while unpaid
 */
public record PaymentInfo(
        String paymentId, String status, @Nullable Instant paidAt) {}
