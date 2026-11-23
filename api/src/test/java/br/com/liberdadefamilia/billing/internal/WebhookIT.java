package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.billing.RefundReason;
import br.com.liberdadefamilia.billing.domain.FounderOffer;
import br.com.liberdadefamilia.billing.domain.OrderStatus;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Asaas calls the webhook; every call is checked, recorded once and applied once. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import({TestcontainersConfiguration.class, WebhookIT.Config.class})
class WebhookIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    private static final String TOKEN = "test-webhook-token-0123456789abcdef0123";
    private static final String URI = "/v1/billing/webhooks/asaas";
    private static final Instant BASE = Instant.parse("2026-12-01T00:00:00Z");
    /** Each test counts its rate-limit budget in a minute of its own. */
    private static final AtomicInteger MINUTES = new AtomicInteger();

    @Autowired
    MockMvcTester mvc;

    @Autowired
    OrderRepository orders;

    @Autowired
    FakePaymentGateway gateway;

    @Autowired
    Plans plans;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestClock clock;

    @MockitoSpyBean
    OrderPayments payments;

    @BeforeEach
    void ownMinute() {
        clock.freeze(BASE.plusSeconds(120L * MINUTES.incrementAndGet()));
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    private record Order(UUID id, UUID owner, UUID ws, String checkout, String payment) {}

    /** An order with a checkout, left in the given status. */
    private Order order(OrderStatus status) {
        UUID owner = users.create("Dono Webhook", null);
        UUID ws = workspaces.createFor(owner);
        UUID id = orders.insert(ws, owner, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);
        String checkout = "chk_wh_" + id;
        orders.setCheckoutId(id, checkout);
        if (status != OrderStatus.CREATED) {
            orders.transition(id, OrderStatus.CREATED, status);
        }
        return new Order(id, owner, ws, checkout, "pay_wh_" + id);
    }

    /** An order that was paid and gave the family its plan. */
    private Order paidOrder() {
        Order order = order(OrderStatus.CREATED);
        payments.applyPaid(order.id, clock.instant(), order.payment);
        return order;
    }

    private static String fixture(String name, String eventId, @Nullable Order order) throws IOException {
        try (InputStream in = WebhookIT.class.getResourceAsStream("/billing/asaas/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("EVENT_ID", eventId)
                    .replace("CHECKOUT_ID", order == null ? "chk_unknown" : order.checkout)
                    .replace("PAYMENT_ID", order == null ? "pay_unknown" : order.payment);
        }
    }

    private MvcTestResult post(String body, @Nullable String token) {
        var request =
                mvc.post().uri(URI).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request = request.header("asaas-access-token", token);
        }
        return request.exchange();
    }

    private MvcTestResult send(String fixture, String eventId, @Nullable Order order) throws IOException {
        return post(fixture(fixture, eventId, order), TOKEN);
    }

    private String newEventId() {
        return "evt_" + UUID.randomUUID();
    }

    private long eventRows() {
        return jdbc.sql("select count(*) from billing_webhook_events")
                .query(Long.class)
                .single();
    }

    private String outcome(String eventId) {
        return jdbc.sql("select outcome from billing_webhook_events where event_id = :id")
                .param("id", eventId)
                .query(String.class)
                .single();
    }

    private OrderStatus status(Order order) {
        return orders.findById(order.id).orElseThrow().status();
    }

    private long audit(String action, UUID ws) {
        return jdbc.sql("select count(*) from audit_events where action = :a and target_id = :t")
                .param("a", action)
                .param("t", ws)
                .query(Long.class)
                .single();
    }

    @Test
    void missingTokenIs401() throws IOException {
        long before = eventRows();

        MvcTestResult result = post(fixture("webhook-checkout-paid.json", newEventId(), null), null);

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("billing.webhook-unauthorized");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Requisição não autorizada.");
        assertThat(eventRows()).isEqualTo(before);
    }

    @Test
    void wrongTokenIs401() throws IOException {
        long before = eventRows();

        MvcTestResult result = post(fixture("webhook-checkout-paid.json", newEventId(), null), TOKEN + "x");

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("billing.webhook-unauthorized");
        assertThat(eventRows()).isEqualTo(before);
    }

    @Test
    void checkoutPaidActivates() throws IOException {
        Order order = order(OrderStatus.CREATED);
        gateway.markPaid(order.checkout, order.payment);
        String eventId = newEventId();

        MvcTestResult result = send("webhook-checkout-paid.json", eventId, order);

        assertThat(result).hasStatus(200);
        assertThat(status(order)).isEqualTo(OrderStatus.PAID);
        assertThat(plans.current(order.ws).key()).isEqualTo(PlanKey.FAMILY);
        assertThat(outcome(eventId)).isEqualTo("APPLIED");
        // The payment id is looked up after the commit and kept for a later refund.
        assertThat(orders.findById(order.id).orElseThrow().paymentId()).isEqualTo(order.payment);
    }

    @Test
    void sameEventTwiceIsIdempotent() throws IOException {
        Order order = order(OrderStatus.CREATED);
        String eventId = newEventId();

        assertThat(send("webhook-checkout-paid.json", eventId, order)).hasStatus(200);
        assertThat(send("webhook-checkout-paid.json", eventId, order)).hasStatus(200);

        assertThat(audit("plan.activated", order.ws)).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from billing_webhook_events where event_id = :id")
                        .param("id", eventId)
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void checkoutExpiredMarksExpired() throws IOException {
        Order order = order(OrderStatus.CREATED);
        String eventId = newEventId();

        assertThat(send("webhook-checkout-expired.json", eventId, order)).hasStatus(200);

        assertThat(status(order)).isEqualTo(OrderStatus.EXPIRED);
        assertThat(outcome(eventId)).isEqualTo("APPLIED");
    }

    @Test
    void checkoutCanceledMarksCanceled() throws IOException {
        Order order = order(OrderStatus.CREATED);

        assertThat(send("webhook-checkout-canceled.json", newEventId(), order)).hasStatus(200);

        assertThat(status(order)).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void expiredAfterPaidIsIgnored() throws IOException {
        Order order = paidOrder();
        String eventId = newEventId();

        assertThat(send("webhook-checkout-expired.json", eventId, order)).hasStatus(200);

        assertThat(status(order)).isEqualTo(OrderStatus.PAID);
        assertThat(outcome(eventId)).isEqualTo("IGNORED");
    }

    @Test
    void unknownCheckoutIs200() throws IOException {
        String eventId = newEventId();

        assertThat(send("webhook-checkout-paid.json", eventId, null)).hasStatus(200);

        assertThat(outcome(eventId)).isEqualTo("UNKNOWN_ORDER");
    }

    @Test
    void refundEventEndsThePlan() throws IOException {
        Order order = paidOrder();
        orders.markRefundPending(order.id, RefundReason.CUSTOMER, clock.instant());
        String eventId = newEventId();

        assertThat(send("webhook-payment-refunded.json", eventId, order)).hasStatus(200);

        assertThat(status(order)).isEqualTo(OrderStatus.REFUNDED);
        assertThat(orders.findById(order.id).orElseThrow().refundedAt()).isEqualTo(clock.instant());
        assertThat(plans.current(order.ws).key()).isEqualTo(PlanKey.FREE);
        assertThat(audit("plan.ended", order.ws)).isEqualTo(1);
        assertThat(outcome(eventId)).isEqualTo("APPLIED");
    }

    @Test
    void refundFromThePanelOnAPaidOrderEndsThePlan() throws IOException {
        Order order = paidOrder();

        assertThat(send("webhook-payment-refunded.json", newEventId(), order)).hasStatus(200);

        assertThat(status(order)).isEqualTo(OrderStatus.REFUNDED);
        assertThat(plans.current(order.ws).key()).isEqualTo(PlanKey.FREE);
        assertThat(audit("plan.ended", order.ws)).isEqualTo(1);
    }

    @Test
    void refundOfADuplicateKeepsTheRunningPlan() throws IOException {
        Order first = paidOrder();
        UUID second = orders.insert(
                first.ws, first.owner, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);
        String checkout = "chk_dup_" + second;
        orders.setCheckoutId(second, checkout);
        orders.transition(second, OrderStatus.CREATED, OrderStatus.CANCELED);
        payments.applyPaid(second, clock.instant(), "pay_dup_" + second);
        Order duplicate = new Order(second, first.owner, first.ws, checkout, "pay_dup_" + second);

        assertThat(send("webhook-payment-refunded.json", newEventId(), duplicate))
                .hasStatus(200);

        assertThat(status(duplicate)).isEqualTo(OrderStatus.REFUNDED);
        assertThat(plans.current(first.ws).key()).isEqualTo(PlanKey.FAMILY);
        assertThat(audit("plan.ended", first.ws)).isZero();
    }

    @Test
    void refundOfAnOrderNotPaidIsIgnored() throws IOException {
        Order order = order(OrderStatus.CREATED);
        String eventId = newEventId();

        assertThat(send("webhook-payment-refunded.json", eventId, order)).hasStatus(200);

        assertThat(status(order)).isEqualTo(OrderStatus.CREATED);
        assertThat(outcome(eventId)).isEqualTo("IGNORED");
    }

    @Test
    void chargebackEndsAndAlerts(CapturedOutput output) throws IOException {
        Order order = paidOrder();

        assertThat(send("webhook-chargeback.json", newEventId(), order)).hasStatus(200);

        assertThat(status(order)).isEqualTo(OrderStatus.CHARGEBACK);
        assertThat(plans.current(order.ws).key()).isEqualTo(PlanKey.FREE);
        assertThat(audit("plan.ended", order.ws)).isEqualTo(1);
        assertThat(output.getAll()).contains("BILLING_ALERT type=CHARGEBACK").doesNotContain("pay_");
    }

    @Test
    void otherEventIsIgnored() throws IOException {
        Order order = paidOrder();
        String eventId = newEventId();

        assertThat(send("webhook-payment-created.json", eventId, order)).hasStatus(200);

        assertThat(outcome(eventId)).isEqualTo("IGNORED");
        assertThat(status(order)).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void ruleErrorReturns200AndAlerts(CapturedOutput output) throws IOException {
        Order order = order(OrderStatus.CREATED);
        doThrow(new IllegalStateException("boom")).when(payments).applyPaid(eq(order.id), any(Instant.class), any());
        String eventId = newEventId();

        MvcTestResult result = send("webhook-checkout-paid.json", eventId, order);

        assertThat(result).hasStatus(200);
        assertThat(outcome(eventId)).isEqualTo("ERROR");
        assertThat(status(order)).isEqualTo(OrderStatus.CREATED);
        assertThat(output.getAll()).contains("BILLING_ALERT type=WEBHOOK_ERROR");
    }

    @Test
    void bodyOver64KbIs413() {
        long before = eventRows();
        byte[] big = new byte[65_537];
        Arrays.fill(big, (byte) ' ');

        MvcTestResult result = mvc.post()
                .uri(URI)
                .contentType(MediaType.APPLICATION_JSON)
                .header("asaas-access-token", TOKEN)
                .content(big)
                .exchange();

        assertThat(result).hasStatus(413);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.too-large");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("A requisição é grande demais.");
        assertThat(eventRows()).isEqualTo(before);
    }

    @Test
    void oversizedWithABadTokenIs413() {
        byte[] big = new byte[65_537];
        Arrays.fill(big, (byte) ' ');

        MvcTestResult result = mvc.post()
                .uri(URI)
                .contentType(MediaType.APPLICATION_JSON)
                .header("asaas-access-token", "wrong")
                .content(big)
                .exchange();

        assertThat(result).hasStatus(413);
    }

    @Test
    void authenticatedCallsAreLimitedAt120() {
        for (int i = 0; i < 120; i++) {
            assertThat(post("{}", TOKEN)).hasStatus(400);
        }

        MvcTestResult last = post("{}", TOKEN);

        assertThat(last).hasStatus(429);
        assertThat(last).bodyJson().extractingPath("$.code").isEqualTo("rate.limited");
        assertThat(last.getResponse().getHeader("Retry-After")).isNotNull();
    }

    @Test
    void badTokenFloodNeverBlocksAsaas() throws IOException {
        Order order = order(OrderStatus.CREATED);
        for (int i = 0; i < 200; i++) {
            assertThat(post("{}", i % 2 == 0 ? null : "wrong").getResponse().getStatus())
                    .isEqualTo(i < 20 ? 401 : 429);
        }

        MvcTestResult valid = send("webhook-checkout-paid.json", newEventId(), order);

        assertThat(valid).hasStatus(200);
        assertThat(status(order)).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void theBadTokenBudgetIsPerClientAddress() {
        for (int i = 0; i < 20; i++) {
            assertThat(post("{}", "wrong")).hasStatus(401);
        }
        MvcTestResult limited = post("{}", "wrong");
        MvcTestResult otherClient = mvc.post()
                .uri(URI)
                .with(request -> {
                    request.setRemoteAddr("203.0.113.9");
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("asaas-access-token", "wrong")
                .exchange();

        assertThat(limited).hasStatus(429);
        assertThat(limited).bodyJson().extractingPath("$.code").isEqualTo("rate.limited");
        assertThat(otherClient).hasStatus(401);
    }

    @Test
    void noCsrfNeeded() throws IOException {
        MvcTestResult result = send("webhook-payment-created.json", newEventId(), null);

        assertThat(result).hasStatus(200);
    }

    @Test
    void bodyIsNotStored() {
        List<String> columns = jdbc.sql("""
                        select column_name from information_schema.columns
                        where table_name = 'billing_webhook_events' order by column_name""").query(String.class).list();

        assertThat(columns)
                .containsExactly(
                        "checkout_id",
                        "event_id",
                        "event_type",
                        "outcome",
                        "payment_id",
                        "processed_at",
                        "received_at");
    }

    @Test
    void eventTimesComeFromTheApplicationClock() throws IOException {
        String eventId = newEventId();

        assertThat(send("webhook-payment-created.json", eventId, null)).hasStatus(200);

        assertThat(jdbc.sql("select received_at, processed_at from billing_webhook_events where event_id = :id")
                        .param("id", eventId)
                        .query((rs, n) -> List.of(
                                rs.getTimestamp("received_at").toInstant(),
                                rs.getTimestamp("processed_at").toInstant()))
                        .single())
                .containsExactly(clock.instant(), clock.instant());
    }
}
