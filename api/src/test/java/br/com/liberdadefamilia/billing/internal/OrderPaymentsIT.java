package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.billing.RefundReason;
import br.com.liberdadefamilia.billing.domain.FounderOffer;
import br.com.liberdadefamilia.billing.domain.OrderStatus;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestUsers;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/** A confirmed payment activates the plan once; a late or double one is refunded. */
@SpringBootTest
@Import({TestcontainersConfiguration.class, OrderPaymentsIT.Config.class})
class OrderPaymentsIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    private static final Instant T = Instant.parse("2026-12-01T00:00:00Z");

    /** Payment ids are unique in the database, which every test shares. */
    private final String pay1 = "pay_1_" + UUID.randomUUID();

    private final String pay2 = "pay_2_" + UUID.randomUUID();

    @Autowired
    OrderPayments payments;

    @Autowired
    OrderRepository orders;

    @Autowired
    Plans plans;

    @Autowired
    FakePaymentGateway gateway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestClock clock;

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    private UUID family() {
        return jdbc.sql("insert into workspaces (name) values ('Familia Pagamento') returning id")
                .query(UUID.class)
                .single();
    }

    /** A new order of the family, left in the given status. */
    private UUID order(UUID ws, OrderStatus status) {
        UUID by = users.create("Dono Pagamento", null);
        UUID id = orders.insert(ws, by, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);
        if (status != OrderStatus.CREATED) {
            orders.transition(id, OrderStatus.CREATED, status);
        }
        return id;
    }

    private long audit(String action, UUID target) {
        return jdbc.sql("select count(*) from audit_events where action = :a and target_id = :t")
                .param("a", action)
                .param("t", target)
                .query(Long.class)
                .single();
    }

    private Instant endsAt(UUID ws) {
        return jdbc.sql("select ends_at from workspace_plans where workspace_id = :ws")
                .param("ws", ws)
                .query(Timestamp.class)
                .single()
                .toInstant();
    }

    @Test
    void paidActivatesForOneYear() {
        clock.freeze(T.plusSeconds(3600));
        UUID ws = family();
        UUID order = order(ws, OrderStatus.CREATED);

        assertThat(payments.applyPaid(order, T, pay1)).isEqualTo(OrderPayments.ApplyResult.ACTIVATED);

        Map<String, Object> plan = jdbc.sql(
                        "select founder, source_order_id, starts_at from workspace_plans where workspace_id = :ws")
                .param("ws", ws)
                .query()
                .singleRow();
        assertThat(endsAt(ws)).isEqualTo(Instant.parse("2027-12-01T00:00:00Z"));
        assertThat(((Timestamp) plan.get("starts_at")).toInstant()).isEqualTo(T);
        assertThat(plan.get("founder")).isEqualTo(true);
        assertThat(plan.get("source_order_id")).isEqualTo(order);
        assertThat(plans.current(ws).key()).isEqualTo(PlanKey.FAMILY);
        var row = orders.findForWorkspace(order, ws).orElseThrow();
        assertThat(row.status()).isEqualTo(OrderStatus.PAID);
        assertThat(row.paymentId()).isEqualTo(pay1);
        assertThat(audit("plan.activated", ws)).isEqualTo(1);
    }

    @Test
    void paidAtAheadOfTheClockIsClamped() {
        Instant now = Instant.parse("2026-12-01T12:00:00Z");
        clock.freeze(now);
        UUID ws = family();
        UUID order = order(ws, OrderStatus.CREATED);

        payments.applyPaid(order, now.plus(5, ChronoUnit.MINUTES), pay1);

        assertThat(orders.findForWorkspace(order, ws).orElseThrow().paidAt()).isEqualTo(now);
        assertThat(endsAt(ws)).isEqualTo(FounderOffer.endsAt(now));
    }

    @Test
    void secondApplyIsNoop() {
        clock.freeze(T.plusSeconds(3600));
        UUID ws = family();
        UUID order = order(ws, OrderStatus.CREATED);

        assertThat(payments.applyPaid(order, T, pay1)).isEqualTo(OrderPayments.ApplyResult.ACTIVATED);
        assertThat(payments.applyPaid(order, T, pay1)).isEqualTo(OrderPayments.ApplyResult.ALREADY_APPLIED);

        assertThat(audit("plan.activated", ws)).isEqualTo(1);
    }

    @Test
    void paidAfterCancelActivates() {
        clock.freeze(T.plusSeconds(3600));
        UUID ws = family();
        UUID order = order(ws, OrderStatus.CANCELED);

        assertThat(payments.applyPaid(order, T, pay1)).isEqualTo(OrderPayments.ApplyResult.ACTIVATED);
        assertThat(plans.current(ws).key()).isEqualTo(PlanKey.FAMILY);
    }

    @Test
    void paidAfterExpireActivates() {
        clock.freeze(T.plusSeconds(3600));
        UUID ws = family();
        UUID order = order(ws, OrderStatus.EXPIRED);

        assertThat(payments.applyPaid(order, T, pay1)).isEqualTo(OrderPayments.ApplyResult.ACTIVATED);
        assertThat(plans.current(ws).key()).isEqualTo(PlanKey.FAMILY);
    }

    @Test
    void secondPaymentWhileActiveRefundsAsDuplicate() {
        clock.freeze(T.plusSeconds(3600));
        UUID ws = family();
        UUID first = order(ws, OrderStatus.CREATED);
        payments.applyPaid(first, T, pay1);
        Instant endsAt = endsAt(ws);
        UUID second = order(ws, OrderStatus.CANCELED);
        String checkout = "chk_second_" + second;
        orders.setCheckoutId(second, checkout);
        gateway.markPaid(checkout, pay2, T);

        assertThat(payments.applyPaid(second, T, pay2)).isEqualTo(OrderPayments.ApplyResult.DUPLICATE_REFUND);

        var row = orders.findForWorkspace(second, ws).orElseThrow();
        assertThat(row.status()).isEqualTo(OrderStatus.REFUND_PENDING);
        assertThat(row.refundReason()).isEqualTo(RefundReason.DUPLICATE);
        assertThat(row.refundRequestedAt()).isEqualTo(clock.instant().truncatedTo(ChronoUnit.MICROS));
        Map<String, Object> plan = jdbc.sql("select source_order_id from workspace_plans where workspace_id = :ws")
                .param("ws", ws)
                .query()
                .singleRow();
        assertThat(plan.get("source_order_id")).isEqualTo(first);
        assertThat(endsAt(ws)).isEqualTo(endsAt);
        // The fake gateway marks a payment REFUNDED when refund("pay_2") reaches it.
        assertThat(gateway.findPayment(checkout).orElseThrow().status()).isEqualTo("REFUNDED");
        assertThat(audit("billing.refund-requested", second)).isEqualTo(1);
        assertThat(audit("plan.activated", ws)).isEqualTo(1);
    }

    @Test
    void refundFailureLeavesTheOrderPendingForTheJob() {
        clock.freeze(T.plusSeconds(3600));
        UUID ws = family();
        payments.applyPaid(order(ws, OrderStatus.CREATED), T, pay1);
        UUID second = order(ws, OrderStatus.CANCELED);
        String checkout = "chk_second_" + second;
        orders.setCheckoutId(second, checkout);
        gateway.markPaid(checkout, pay2, T);
        gateway.failNextRefund();

        assertThat(payments.applyPaid(second, T, pay2)).isEqualTo(OrderPayments.ApplyResult.DUPLICATE_REFUND);

        assertThat(orders.findForWorkspace(second, ws).orElseThrow().status()).isEqualTo(OrderStatus.REFUND_PENDING);
        assertThat(gateway.findPayment(checkout).orElseThrow().status()).isEqualTo("RECEIVED");
    }

    @Test
    void customerRefundEndsThePlanNow() {
        Instant now = T.plusSeconds(3600);
        clock.freeze(now);
        UUID ws = family();
        UUID order = order(ws, OrderStatus.CREATED);
        payments.applyPaid(order, T, pay1);

        payments.requestRefund(order, RefundReason.CUSTOMER);

        assertThat(orders.findForWorkspace(order, ws).orElseThrow().status()).isEqualTo(OrderStatus.REFUND_PENDING);
        assertThat(plans.current(ws).key()).isEqualTo(PlanKey.FREE);
        assertThat(endsAt(ws)).isEqualTo(now);
        assertThat(audit("plan.ended", ws)).isEqualTo(1);
        assertThat(jdbc.sql(
                                "select metadata->>'reason' from audit_events where action = 'plan.ended' and target_id = :t")
                        .param("t", ws)
                        .query(String.class)
                        .single())
                .isEqualTo("CUSTOMER_REFUND");
    }

    @Test
    void paymentAfterFamilyDeletionRefunds() {
        clock.freeze(T.plusSeconds(3600));
        UUID ws = family();
        UUID order = order(ws, OrderStatus.CANCELED);
        jdbc.sql("delete from workspaces where id = :ws").param("ws", ws).update();

        assertThat(payments.applyPaid(order, T, pay1)).isEqualTo(OrderPayments.ApplyResult.DUPLICATE_REFUND);

        assertThat(jdbc.sql("select status from billing_orders where id = :id")
                        .param("id", order)
                        .query(String.class)
                        .single())
                .isEqualTo("REFUND_PENDING");
    }

    @Test
    void paidAfterPlanExpiredActivatesAgain() {
        clock.freeze(T.plusSeconds(3600));
        UUID ws = family();
        payments.applyPaid(order(ws, OrderStatus.CREATED), T, pay1);
        Instant later = Instant.parse("2027-12-02T00:00:00Z");
        clock.freeze(later);
        UUID second = order(ws, OrderStatus.CREATED);

        assertThat(payments.applyPaid(second, later, pay2)).isEqualTo(OrderPayments.ApplyResult.ACTIVATED);

        assertThat(endsAt(ws)).isEqualTo(Instant.parse("2028-12-02T00:00:00Z"));
        assertThat(plans.current(ws).key()).isEqualTo(PlanKey.FAMILY);
    }
}
