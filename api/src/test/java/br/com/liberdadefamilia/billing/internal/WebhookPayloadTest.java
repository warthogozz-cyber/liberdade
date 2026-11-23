package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** What billing reads from an Asaas call, and what it leaves out. */
class WebhookPayloadTest {

    private final JsonMapper json = JsonMapper.builder().build();

    private AsaasEvent read(String fixture) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/billing/asaas/" + fixture)) {
            return json.readValue(in, AsaasEvent.class);
        }
    }

    @Test
    void checkoutEventKeepsOnlyTheIds() throws IOException {
        AsaasEvent event = read("webhook-checkout-paid.json");

        assertThat(event.id()).isEqualTo("EVENT_ID");
        assertThat(event.event()).isEqualTo("CHECKOUT_PAID");
        assertThat(event.checkout()).isEqualTo(new AsaasEvent.Checkout("CHECKOUT_ID"));
        assertThat(event.payment()).isNull();
    }

    @Test
    void paymentEventKeepsOnlyTheIds() throws IOException {
        AsaasEvent event = read("webhook-chargeback.json");

        assertThat(event.event()).isEqualTo("PAYMENT_CHARGEBACK_REQUESTED");
        assertThat(event.payment()).isEqualTo(new AsaasEvent.Payment("PAYMENT_ID", "CHECKOUT_ID"));
        assertThat(event.checkout()).isNull();
    }
}
