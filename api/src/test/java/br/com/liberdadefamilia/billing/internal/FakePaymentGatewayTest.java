package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakePaymentGatewayTest {

    private final FakePaymentGateway gateway = new FakePaymentGateway(new BillingProperties(
            true, true, "fake", List.of("checkout.e2e.test"), "", new BillingProperties.Asaas("", "")));

    @Test
    void linksUseTheE2eHost() {
        UUID orderId = UUID.randomUUID();

        CheckoutSession session = gateway.createCheckout(new CheckoutRequest(orderId, "https://app.test/x"));

        assertThat(session.checkoutId()).isEqualTo("chk_fake_" + orderId);
        assertThat(session.link()).isEqualTo(URI.create("https://checkout.e2e.test/c/chk_fake_" + orderId));
    }

    @Test
    void failNextRefund() {
        gateway.failNextRefund();

        assertThatThrownBy(() -> gateway.refund("pay_1"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessage("asaas status 400")
                .satisfies(
                        e -> assertThat(((PaymentGatewayException) e).status()).isEqualTo(400));
        assertThatCode(() -> gateway.refund("pay_1")).doesNotThrowAnyException();
    }

    @Test
    void paidCheckoutsAreFoundAndRefundsMarkThemRefunded() {
        assertThat(gateway.findPayment("chk_x")).isEmpty();

        gateway.markPaid("chk_x", "pay_x");
        assertThat(gateway.findPayment("chk_x")).contains(new PaymentInfo("pay_x", "RECEIVED"));

        gateway.refund("pay_x");
        assertThat(gateway.findPayment("chk_x")).contains(new PaymentInfo("pay_x", "REFUNDED"));
    }

    @Test
    void markRefundedFlipsTheStatus() {
        gateway.markPaid("chk_y", "pay_y");

        gateway.markRefunded("pay_y");

        assertThat(gateway.findPayment("chk_y")).contains(new PaymentInfo("pay_y", "REFUNDED"));
    }
}
