package br.com.liberdadefamilia.billing.internal;

import java.util.Optional;

/** The payment provider, as the billing module needs it. */
public interface PaymentGateway {

    /** Opens a checkout for the founder offer. */
    CheckoutSession createCheckout(CheckoutRequest request);

    /** The payment made through a checkout, or empty when there is none yet. */
    Optional<PaymentInfo> findPayment(String checkoutId);

    /** Refunds a payment in full. */
    void refund(String paymentId);
}
