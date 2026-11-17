package br.com.liberdadefamilia.billing.internal;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** The gateway for dev, e2e and tests: no network, state in memory. Refused under the prod profile. */
@Component
@ConditionalOnProperty(name = "liberdade.billing.gateway", havingValue = "fake")
class FakePaymentGateway implements PaymentGateway {

    private static final String DEFAULT_HOST = "checkout.e2e.test";

    private final String host;
    private final Map<String, PaymentInfo> paymentsByCheckout = new ConcurrentHashMap<>();
    private final AtomicBoolean failNextRefund = new AtomicBoolean();

    FakePaymentGateway(BillingProperties properties) {
        this.host = properties.checkoutHosts().isEmpty()
                ? DEFAULT_HOST
                : properties.checkoutHosts().get(0);
    }

    @Override
    public CheckoutSession createCheckout(CheckoutRequest request) {
        String checkoutId = "chk_fake_" + request.orderId();
        return new CheckoutSession(checkoutId, URI.create("https://" + host + "/c/" + checkoutId));
    }

    @Override
    public Optional<PaymentInfo> findPayment(String checkoutId) {
        return Optional.ofNullable(paymentsByCheckout.get(checkoutId));
    }

    @Override
    public void refund(String paymentId) {
        if (failNextRefund.getAndSet(false)) {
            throw new PaymentGatewayException(400);
        }
        markRefunded(paymentId);
    }

    /** Test only: the payer paid this checkout. */
    void markPaid(String checkoutId, String paymentId, Instant paidAt) {
        paymentsByCheckout.put(checkoutId, new PaymentInfo(paymentId, "RECEIVED", paidAt));
    }

    /** Test only: the next {@link #refund} fails once with a 400. */
    void failNextRefund() {
        failNextRefund.set(true);
    }

    /** Test only: the payment was refunded, by the refund call or on the gateway's side. */
    void markRefunded(String paymentId) {
        paymentsByCheckout.replaceAll((checkoutId, info) ->
                info.paymentId().equals(paymentId) ? new PaymentInfo(paymentId, "REFUNDED", info.paidAt()) : info);
    }
}
