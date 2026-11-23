package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.RefundReason;
import br.com.liberdadefamilia.billing.domain.FounderOffer;
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

/** What the plan page reads. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PlanEndpointIT.Config.class})
class PlanEndpointIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    private static final Instant BASE = Instant.parse("2026-12-01T12:00:00Z");
    /** BASE is 09:00 on 01/12 in São Paulo, so the window runs through 08/12 and closes at 00:00 on 09/12. */
    private static final String LAST_REFUND_DAY_ENDS = "2026-12-09T03:00:00Z";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    OrderRepository orders;

    @Autowired
    OrderPayments payments;

    @Autowired
    FakePaymentGateway gateway;

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

    private UUID member(Family f, Role role) {
        UUID user = users.create("Membro", null);
        workspaces.addMember(f.ws, user, role);
        return user;
    }

    private UUID newOrder(Family f) {
        return orders.insert(f.ws, f.owner, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);
    }

    /** An order paid at BASE, which gave the family its plan. */
    private UUID paidOrder(Family f) {
        clock.freeze(BASE);
        UUID id = newOrder(f);
        orders.setCheckoutId(id, "chk_fake_" + id);
        gateway.markPaid("chk_fake_" + id, "pay_" + id);
        payments.applyPaid(id, BASE, "pay_" + id);
        return id;
    }

    private MvcTestResult getPlan(UUID user, UUID ws) {
        return mvc.get()
                .uri("/v1/workspaces/" + ws + "/billing/plan")
                .with(TestUsers.signedIn(user, null))
                .exchange();
    }

    @Test
    void freeOwnerCanPurchase() {
        Family f = family();

        MvcTestResult result = getPlan(f.owner, f.ws);

        assertThat(result).hasStatus(200);
        var body = assertThat(result).bodyJson();
        body.extractingPath("$.key").isEqualTo("FREE");
        body.extractingPath("$.founder").isEqualTo(false);
        body.extractingPath("$.canPurchase").isEqualTo(true);
        body.extractingPath("$.presaleOpen").isEqualTo(true);
        body.extractingPath("$.canRefund").isEqualTo(false);
        body.extractingPath("$.theme").isEqualTo("TEAL");
        body.extractingPath("$.openOrder").isNull();
        body.extractingPath("$.paidOrderId").isNull();
        body.extractingPath("$.refundDeadline").isNull();
    }

    @Test
    void adultSeesButCannotPurchase() {
        Family f = family();

        MvcTestResult result = getPlan(member(f, Role.ADULT), f.ws);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.canPurchase").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.presaleOpen").isEqualTo(true);
    }

    @Test
    void founderWithinSevenDays() {
        Family f = family();
        UUID order = paidOrder(f);
        clock.freeze(BASE.plus(Duration.ofDays(2)));

        MvcTestResult result = getPlan(f.owner, f.ws);

        var body = assertThat(result).bodyJson();
        body.extractingPath("$.key").isEqualTo("FAMILY");
        body.extractingPath("$.founder").isEqualTo(true);
        body.extractingPath("$.canRefund").isEqualTo(true);
        body.extractingPath("$.canPurchase").isEqualTo(false);
        body.extractingPath("$.paidOrderId").isEqualTo(order.toString());
        body.extractingPath("$.refundDeadline").isEqualTo(LAST_REFUND_DAY_ENDS);
        body.extractingPath("$.endsAt").isNotNull();
    }

    @Test
    void canRefundMatchesTheRefundRouteAtTheDeadline() {
        Family f = family();
        paidOrder(f);
        Instant deadline = Instant.parse(LAST_REFUND_DAY_ENDS);

        clock.freeze(deadline.minusSeconds(1));
        assertThat(getPlan(f.owner, f.ws))
                .bodyJson()
                .extractingPath("$.canRefund")
                .isEqualTo(true);

        clock.freeze(deadline);
        assertThat(getPlan(f.owner, f.ws))
                .bodyJson()
                .extractingPath("$.canRefund")
                .isEqualTo(false);
    }

    @Test
    void adultNeverCanRefund() {
        Family f = family();
        paidOrder(f);

        MvcTestResult result = getPlan(member(f, Role.ADULT), f.ws);

        assertThat(result).bodyJson().extractingPath("$.canRefund").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.key").isEqualTo("FAMILY");
    }

    @Test
    void founderAfterSevenDays() {
        Family f = family();
        paidOrder(f);
        clock.freeze(BASE.plus(Duration.ofDays(8)));

        MvcTestResult result = getPlan(f.owner, f.ws);

        var body = assertThat(result).bodyJson();
        body.extractingPath("$.key").isEqualTo("FAMILY");
        body.extractingPath("$.canRefund").isEqualTo(false);
        body.extractingPath("$.refundDeadline").isEqualTo(LAST_REFUND_DAY_ENDS);
    }

    @Test
    void duplicateShowsOpenOrder() {
        Family f = family();
        paidOrder(f);
        UUID duplicate = newOrder(f);
        payments.applyPaid(duplicate, BASE, null);
        assertThat(orders.findById(duplicate).orElseThrow().refundReason()).isEqualTo(RefundReason.DUPLICATE);

        MvcTestResult result = getPlan(f.owner, f.ws);

        var body = assertThat(result).bodyJson();
        body.extractingPath("$.openOrder.id").isEqualTo(duplicate.toString());
        body.extractingPath("$.openOrder.status").isEqualTo("REFUND_PENDING");
        body.extractingPath("$.openOrder.refundReason").isEqualTo("DUPLICATE");
        body.extractingPath("$.canRefund").isEqualTo(true);
    }
}
