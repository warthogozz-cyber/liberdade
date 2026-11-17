package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AsaasGatewayTest {

    private static final String BASE = "https://asaas.test";

    private MockRestServiceServer server;
    private AsaasGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        BillingProperties properties = new BillingProperties(
                true, true, "asaas", List.of("sandbox.asaas.com"), "", new BillingProperties.Asaas(BASE, "test-key"));
        gateway = new AsaasGateway(builder, properties);
    }

    private static String json(String name) throws IOException {
        return new ClassPathResource("billing/asaas/" + name).getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void createCheckoutSendsTheFounderItem() throws IOException {
        UUID orderId = UUID.randomUUID();
        String callback = "https://app.test/mais/plano?pedido=" + orderId;
        server.expect(requestTo(BASE + "/v3/checkouts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", "test-key"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"value\":89.00")))
                .andExpect(jsonPath("$.items[0].value").value(89.00))
                .andExpect(jsonPath("$.items[0].name").value("Família Fundadora"))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items[0].imageBase64").isNotEmpty())
                .andExpect(jsonPath("$.chargeTypes[0]").value("DETACHED"))
                .andExpect(jsonPath("$.billingTypes[0]").value("PIX"))
                .andExpect(jsonPath("$.billingTypes[1]").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.billingTypes.length()").value(2))
                .andExpect(jsonPath("$.minutesToExpire").value(60))
                .andExpect(jsonPath("$.externalReference").value(orderId.toString()))
                .andExpect(jsonPath("$.callback.successUrl").value(callback))
                .andExpect(jsonPath("$.callback.cancelUrl").value(callback))
                .andExpect(jsonPath("$.callback.expiredUrl").value(callback))
                .andExpect(jsonPath("$.customerData").doesNotExist())
                .andRespond(withSuccess(json("checkout-created.json"), MediaType.APPLICATION_JSON));

        CheckoutSession session = gateway.createCheckout(new CheckoutRequest(orderId, callback));

        assertThat(session)
                .isEqualTo(
                        new CheckoutSession(
                                "8f2c1a7e-0000-4000-8000-000000000001",
                                URI.create(
                                        "https://sandbox.asaas.com/checkoutSession/show?id=8f2c1a7e-0000-4000-8000-000000000001")));
        server.verify();
    }

    @Test
    void findPaymentByCheckoutSession() throws IOException {
        server.expect(requestTo(BASE + "/v3/payments?checkoutSession=chk_1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", "test-key"))
                .andRespond(withSuccess(json("payments-by-checkout.json"), MediaType.APPLICATION_JSON));

        Optional<PaymentInfo> info = gateway.findPayment("chk_1");

        assertThat(info).contains(new PaymentInfo("pay_123", "RECEIVED", Instant.parse("2026-11-17T03:00:00Z")));
        server.verify();
    }

    @Test
    void findPaymentEmpty() {
        server.expect(requestTo(BASE + "/v3/payments?checkoutSession=chk_1"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThat(gateway.findPayment("chk_1")).isEmpty();
    }

    @Test
    void refundPostsWithoutValue() {
        server.expect(requestTo(BASE + "/v3/payments/pay_123/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", "test-key"))
                .andExpect(content().string("{}"))
                .andRespond(withSuccess("{\"id\":\"pay_123\",\"status\":\"REFUNDED\"}", MediaType.APPLICATION_JSON));

        gateway.refund("pay_123");

        server.verify();
    }

    @Test
    void errorsNeverCarryTheBody() {
        server.expect(requestTo(BASE + "/v3/payments/pay_123/refund"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"description\":\"segredo\"}]}"));

        assertThatThrownBy(() -> gateway.refund("pay_123"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessage("asaas status 400")
                .hasMessageNotContaining("segredo");
    }

    @Test
    void ioErrorsBecomeStatusZero() {
        server.expect(requestTo(BASE + "/v3/payments?checkoutSession=chk_1")).andRespond(request -> {
            throw new IOException("boom segredo");
        });

        assertThatThrownBy(() -> gateway.findPayment("chk_1"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessage("asaas status 0");
    }

    @Test
    void findPaymentEncodesTheCheckoutId() {
        server.expect(requestTo(BASE + "/v3/payments?checkoutSession=a%26b%3Dc"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThat(gateway.findPayment("a&b=c")).isEmpty();
        server.verify();
    }

    @Test
    void findPaymentAcceptsBracesInTheId() {
        server.expect(requestTo(BASE + "/v3/payments?checkoutSession=%7Bx%7D"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThat(gateway.findPayment("{x}")).isEmpty();
    }

    @Test
    void theBeanIsCreatableWithoutAKey() {
        BillingProperties noKey =
                new BillingProperties(true, true, "asaas", List.of(), "", new BillingProperties.Asaas("", ""));

        assertThatCode(() -> new AsaasGateway(noKey)).doesNotThrowAnyException();
    }

    @Test
    void blankLinkIsAGatewayError() {
        server.expect(requestTo(BASE + "/v3/checkouts"))
                .andRespond(withSuccess("{\"id\":\"c1\",\"link\":\"\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.createCheckout(new CheckoutRequest(UUID.randomUUID(), "https://app.test/x")))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessage("asaas status 0");
    }

    @Test
    void badPaymentDateIsAGatewayError() {
        server.expect(requestTo(BASE + "/v3/payments?checkoutSession=chk_1"))
                .andRespond(withSuccess(
                        "{\"data\":[{\"id\":\"p\",\"status\":\"RECEIVED\",\"paymentDate\":\"17/11/2026\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.findPayment("chk_1"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessage("asaas status 0");
    }
}
