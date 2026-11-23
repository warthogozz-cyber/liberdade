package br.com.liberdadefamilia.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.domain.FounderOffer;
import br.com.liberdadefamilia.billing.internal.CheckoutRequest;
import br.com.liberdadefamilia.billing.internal.CheckoutSession;
import br.com.liberdadefamilia.billing.internal.PaymentGateway;
import br.com.liberdadefamilia.billing.internal.PaymentGatewayException;
import br.com.liberdadefamilia.billing.internal.PaymentInfo;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Starting the Founder checkout: who may, what is stored and what happens when the gateway misbehaves. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import({TestcontainersConfiguration.class, CheckoutIT.ScriptedGateway.class})
class CheckoutIT {

    private static final String DETAIL_FAILED = "Não conseguimos abrir o pagamento agora. Tente de novo em instantes.";

    /** The gateway of these tests: it answers like the fake one until a test says otherwise. */
    @TestConfiguration(proxyBeanMethods = false)
    static class ScriptedGateway {

        static volatile Function<CheckoutRequest, CheckoutSession> script = ScriptedGateway::normal;

        static CheckoutSession normal(CheckoutRequest request) {
            String id = "chk_fake_" + request.orderId();
            return new CheckoutSession(id, URI.create("https://checkout.e2e.test/c/" + id));
        }

        @Bean
        @Primary
        PaymentGateway scriptedGateway() {
            return new PaymentGateway() {
                @Override
                public CheckoutSession createCheckout(CheckoutRequest request) {
                    return script.apply(request);
                }

                @Override
                public Optional<PaymentInfo> findPayment(String checkoutId) {
                    return Optional.empty();
                }

                @Override
                public void refund(String paymentId) {}
            };
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @AfterEach
    void resetGateway() {
        ScriptedGateway.script = ScriptedGateway::normal;
    }

    @Test
    void ownerStartsCheckout() throws Exception {
        Family f = family();

        MvcTestResult result = checkout(f.owner, f.ws);

        assertThat(result).hasStatus(201);
        UUID orderId = UUID.fromString(JsonPath.read(body(result), "$.orderId"));
        assertThat(JsonPath.<String>read(body(result), "$.checkoutUrl"))
                .isEqualTo("https://checkout.e2e.test/c/chk_fake_" + orderId);
        Map<String, Object> order = jdbc.sql("""
                        select status, asaas_checkout_id, amount_cents, currency, product, requested_by
                        from billing_orders where id = :id""").param("id", orderId).query().singleRow();
        assertThat(order.get("status")).isEqualTo("CREATED");
        assertThat(order.get("asaas_checkout_id")).isEqualTo("chk_fake_" + orderId);
        assertThat(order.get("amount_cents")).isEqualTo(8900L);
        assertThat(order.get("currency")).isEqualTo("BRL");
        assertThat(order.get("product")).isEqualTo("FOUNDER_2026");
        assertThat(order.get("requested_by")).isEqualTo(f.owner);
        Map<String, Object> audit = jdbc.sql("""
                        select target_type, workspace_id, actor_user_id, metadata->>'product' as product
                        from audit_events where action = 'billing.checkout-started' and target_id = :id""").param("id", orderId).query().singleRow();
        assertThat(audit.get("target_type")).isEqualTo("ORDER");
        assertThat(audit.get("workspace_id")).isEqualTo(f.ws);
        assertThat(audit.get("actor_user_id")).isEqualTo(f.owner);
        assertThat(audit.get("product")).isEqualTo("FOUNDER_2026");
        // AuditListeners spells the product out; a new product must change it too.
        assertThat(audit.get("product")).isEqualTo(FounderOffer.PRODUCT);
    }

    @Test
    void secondCheckoutCancelsTheFirst() throws Exception {
        Family f = family();

        UUID first = orderId(checkout(f.owner, f.ws));
        UUID second = orderId(checkout(f.owner, f.ws));

        assertThat(status(first)).isEqualTo("CANCELED");
        assertThat(status(second)).isEqualTo("CREATED");
    }

    @Test
    void adultCannotBuy() {
        Family f = family();
        UUID adult = users.create("Adulto", null);
        workspaces.addMember(f.ws, adult, Role.ADULT);

        MvcTestResult result = checkout(adult, f.ws);

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
    }

    @Test
    void otherFamilyIsNotFound() {
        Family f = family();
        Family other = family();

        assertThat(checkout(other.owner, f.ws)).hasStatus(404);
    }

    @Test
    void activePlanCannotBuyAgain() {
        Family f = family();
        jdbc.sql("""
                        insert into workspace_plans (workspace_id, plan_key, founder, starts_at, ends_at)
                        values (:ws, 'FAMILY', true, now(), now() + interval '300 days')""").param("ws", f.ws).update();

        MvcTestResult result = checkout(f.owner, f.ws);

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("billing.plan-active");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Sua família já tem um plano ativo.");
    }

    @Test
    void sixthCheckoutInTenMinutesIsRateLimited() {
        Family f = family();
        for (int i = 0; i < 5; i++) {
            assertThat(checkout(f.owner, f.ws)).hasStatus(201);
        }

        MvcTestResult sixth = checkout(f.owner, f.ws);

        assertThat(sixth).hasStatus(429);
        assertThat(sixth).bodyJson().extractingPath("$.code").isEqualTo("rate.limited");
        assertThat(sixth.getResponse().getHeader("Retry-After")).isNotNull();
    }

    @Test
    void linkOutsideAllowedHostsCancels() throws Exception {
        ScriptedGateway.script = request -> new CheckoutSession("chk_evil", URI.create("https://evil.example/c/x"));
        Family f = family();

        MvcTestResult result = checkout(f.owner, f.ws);

        assertFailedAndCanceled(result, f.ws);
        assertThat(body(result)).doesNotContain("evil.example");
    }

    @Test
    void lookalikeHostIsRejected() throws Exception {
        ScriptedGateway.script =
                request -> new CheckoutSession("chk_evil", URI.create("https://checkout.e2e.test.evil.example/c/x"));
        Family f = family();

        assertFailedAndCanceled(checkout(f.owner, f.ws), f.ws);
    }

    @Test
    void httpLinkIsRejected() {
        ScriptedGateway.script = request -> new CheckoutSession("chk_http", URI.create("http://checkout.e2e.test/c/x"));
        Family f = family();

        assertFailedAndCanceled(checkout(f.owner, f.ws), f.ws);
    }

    @Test
    void linkOnAnotherPortIsRejected() {
        ScriptedGateway.script =
                request -> new CheckoutSession("chk_port", URI.create("https://checkout.e2e.test:8443/c/x"));
        Family f = family();

        assertFailedAndCanceled(checkout(f.owner, f.ws), f.ws);
    }

    @Test
    void linkOnTheDefaultPortIsAllowed() {
        ScriptedGateway.script =
                request -> new CheckoutSession("chk_443", URI.create("https://checkout.e2e.test:443/c/x"));
        Family f = family();

        assertThat(checkout(f.owner, f.ws)).hasStatus(201);
    }

    @Test
    void unexpectedGatewayErrorCancels(CapturedOutput output) throws Exception {
        ScriptedGateway.script = request -> {
            throw new IllegalStateException("boom with secret detail");
        };
        Family f = family();
        int before = output.getAll().length();

        MvcTestResult result = checkout(f.owner, f.ws);

        assertFailedAndCanceled(result, f.ws);
        String logged = output.getAll().substring(before);
        assertThat(logged)
                .contains("checkout failed at the gateway (" + IllegalStateException.class.getName() + ")")
                .doesNotContain("boom with secret detail");
    }

    @Test
    void failuresDoNotLogTheOrderId(CapturedOutput output) throws Exception {
        ScriptedGateway.script = request -> {
            throw new PaymentGatewayException(500);
        };
        Family f = family();
        int before = output.getAll().length();

        checkout(f.owner, f.ws);
        ScriptedGateway.script = request -> new CheckoutSession("chk_evil", URI.create("https://evil.example/c/x"));
        checkout(f.owner, f.ws);

        String logged = output.getAll().substring(before);
        assertThat(logged).contains("checkout failed at the gateway").contains("outside the allowed hosts");
        for (UUID id : orderIds(f.ws)) {
            assertThat(logged).doesNotContain(id.toString());
        }
    }

    @Test
    void gatewayFailureCancels() throws Exception {
        ScriptedGateway.script = request -> {
            throw new PaymentGatewayException(500);
        };
        Family f = family();

        MvcTestResult result = checkout(f.owner, f.ws);

        assertFailedAndCanceled(result, f.ws);
        assertThat(body(result)).doesNotContain("asaas status");
    }

    /** With selling closed, the same call is refused before anything is stored. */
    @Nested
    @TestPropertySource(properties = "liberdade.billing.presale-open=false")
    class PresaleClosed {

        @Test
        void presaleClosedIsUnavailable() {
            Family f = family();

            MvcTestResult result = checkout(f.owner, f.ws);

            assertThat(result).hasStatus(409);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("billing.unavailable");
            assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("A compra não está disponível agora.");
            assertThat(orderStatuses(f.ws)).isEmpty();
        }
    }

    private void assertFailedAndCanceled(MvcTestResult result, UUID ws) {
        assertThat(result).hasStatus(502);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("billing.checkout-failed");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(DETAIL_FAILED);
        assertThat(orderStatuses(ws)).containsExactly("CANCELED");
    }

    private List<String> orderStatuses(UUID ws) {
        return jdbc.sql("select status from billing_orders where workspace_id = :ws order by created_at")
                .param("ws", ws)
                .query(String.class)
                .list();
    }

    private List<UUID> orderIds(UUID ws) {
        return jdbc.sql("select id from billing_orders where workspace_id = :ws")
                .param("ws", ws)
                .query(UUID.class)
                .list();
    }

    private String status(UUID orderId) {
        return jdbc.sql("select status from billing_orders where id = :id")
                .param("id", orderId)
                .query(String.class)
                .single();
    }

    private UUID orderId(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(201);
        return UUID.fromString(JsonPath.read(body(result), "$.orderId"));
    }

    private static String body(MvcTestResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private MvcTestResult checkout(UUID user, UUID ws) {
        var session = TestUsers.signedIn(user, null);
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/billing/checkout")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();
    }

    private record Family(UUID owner, UUID ws) {}

    private Family family() {
        UUID owner = users.create("Dono", null);
        return new Family(owner, workspaces.createFor(owner));
    }
}
