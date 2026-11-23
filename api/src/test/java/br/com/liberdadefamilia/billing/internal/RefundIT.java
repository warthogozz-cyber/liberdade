package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.billing.RefundReason;
import br.com.liberdadefamilia.billing.domain.FounderOffer;
import br.com.liberdadefamilia.billing.domain.OrderStatus;
import br.com.liberdadefamilia.billing.domain.RefundWindow;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The owner takes the payment back within seven days. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RefundIT.Config.class})
class RefundIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    private static final Instant BASE = Instant.parse("2026-12-01T12:00:00Z");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    OrderRepository orders;

    @Autowired
    OrderPayments payments;

    @Autowired
    FakePaymentGateway gateway;

    @Autowired
    Plans plans;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestClock clock;

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    private record Family(UUID owner, UUID ws) {}

    private Family family() {
        UUID owner = users.create("Dono", null);
        return new Family(owner, workspaces.createFor(owner));
    }

    private UUID newOrder(Family f) {
        return orders.insert(f.ws, f.owner, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);
    }

    /** An order paid at BASE, which gave the family its plan. */
    private UUID paidOrder(Family f) {
        clock.freeze(BASE);
        UUID id = newOrder(f);
        orders.setCheckoutId(id, "chk_fake_" + id);
        gateway.markPaid("chk_fake_" + id, "pay_" + id, BASE);
        payments.applyPaid(id, BASE, "pay_" + id);
        return id;
    }

    private MvcTestResult refund(UUID user, UUID ws, UUID order) {
        var session = TestUsers.signedIn(user, null);
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/billing/orders/" + order + "/refund")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();
    }

    private String gatewayStatus(UUID order) {
        return gateway.findPayment("chk_fake_" + order).orElseThrow().status();
    }

    @Test
    void refundAtDeadlineMinusOneSecond() {
        Family f = family();
        UUID order = paidOrder(f);
        clock.freeze(RefundWindow.deadline(BASE).minusSeconds(1));

        MvcTestResult result = refund(f.owner, f.ws, order);

        assertThat(result).hasStatus(202);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("REFUND_PENDING");
        assertThat(result).bodyJson().extractingPath("$.refundReason").isEqualTo("CUSTOMER");
        var row = orders.findById(order).orElseThrow();
        assertThat(row.status()).isEqualTo(OrderStatus.REFUND_PENDING);
        assertThat(row.refundReason()).isEqualTo(RefundReason.CUSTOMER);
        assertThat(plans.current(f.ws).key()).isEqualTo(PlanKey.FREE);
        assertThat(gatewayStatus(order)).isEqualTo("REFUNDED");
    }

    @Test
    void refundAtDeadline() {
        Family f = family();
        UUID order = paidOrder(f);
        clock.freeze(RefundWindow.deadline(BASE));

        MvcTestResult result = refund(f.owner, f.ws, order);

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("billing.refund-window-closed");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O prazo de 7 dias para o reembolso já passou.");
        assertThat(orders.findById(order).orElseThrow().status()).isEqualTo(OrderStatus.PAID);
        assertThat(plans.current(f.ws).key()).isEqualTo(PlanKey.FAMILY);
    }

    @Test
    void adultCannotRefund() {
        Family f = family();
        UUID adult = users.create("Bia", null);
        workspaces.addMember(f.ws, adult, Role.ADULT);
        UUID order = paidOrder(f);

        assertThat(refund(adult, f.ws, order)).hasStatus(403);
        assertThat(plans.current(f.ws).key()).isEqualTo(PlanKey.FAMILY);
    }

    @Test
    void otherFamilyOrderIsNotFound() {
        Family f = family();
        Family other = family();
        UUID foreign = paidOrder(other);

        MvcTestResult result = refund(f.owner, f.ws, foreign);

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("billing.order-not-found");
        assertThat(orders.findById(foreign).orElseThrow().status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void refundOfNonPaidIsNotAllowed() {
        Family f = family();
        UUID order = newOrder(f);

        MvcTestResult result = refund(f.owner, f.ws, order);

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("billing.refund-not-allowed");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Esse pedido não pode ser reembolsado.");
    }

    @Test
    void secondRefundIsNotAllowed() {
        Family f = family();
        UUID order = paidOrder(f);
        assertThat(refund(f.owner, f.ws, order)).hasStatus(202);

        MvcTestResult second = refund(f.owner, f.ws, order);

        assertThat(second).hasStatus(409);
        assertThat(second).bodyJson().extractingPath("$.code").isEqualTo("billing.refund-not-allowed");
    }

    @Test
    void duplicateOrderIsNotAllowed() {
        Family f = family();
        paidOrder(f);
        // A second payment while the plan runs is refunded as a duplicate and never gives a plan.
        UUID duplicate = newOrder(f);
        payments.applyPaid(duplicate, BASE, null);
        assertThat(orders.findById(duplicate).orElseThrow().refundReason()).isEqualTo(RefundReason.DUPLICATE);

        MvcTestResult result = refund(f.owner, f.ws, duplicate);

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("billing.refund-not-allowed");
        assertThat(plans.current(f.ws).key()).isEqualTo(PlanKey.FAMILY);
    }

    @Test
    void orderThatDidNotOriginateTheCurrentPlanIsNotAllowed() {
        Family f = family();
        UUID order = paidOrder(f);
        // The plan has run out although the order is still PAID.
        clock.freeze(FounderOffer.endsAt(BASE).plusSeconds(1));
        assertThat(plans.current(f.ws).key()).isEqualTo(PlanKey.FREE);

        MvcTestResult result = refund(f.owner, f.ws, order);

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("billing.refund-not-allowed");
    }

    @Test
    void refundRefusedStaysPendingAndPlanEnded() {
        Family f = family();
        UUID order = paidOrder(f);
        clock.freeze(BASE.plus(Duration.ofDays(1)));
        gateway.failNextRefund();

        MvcTestResult result = refund(f.owner, f.ws, order);

        assertThat(result).hasStatus(202);
        assertThat(orders.findById(order).orElseThrow().status()).isEqualTo(OrderStatus.REFUND_PENDING);
        assertThat(plans.current(f.ws).key()).isEqualTo(PlanKey.FREE);
        assertThat(gatewayStatus(order)).isEqualTo("RECEIVED");
    }
}
