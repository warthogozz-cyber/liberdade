package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.domain.FounderOffer;
import br.com.liberdadefamilia.billing.domain.FounderTheme;
import br.com.liberdadefamilia.billing.domain.OrderStatus;
import br.com.liberdadefamilia.billing.domain.RefundReason;
import br.com.liberdadefamilia.support.TestUsers;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The conditional updates the later payment flows rely on. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BillingRepositoriesIT {

    private static final Instant PAID_AT = Instant.parse("2026-12-01T10:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Autowired
    OrderRepository orders;

    @Autowired
    PlanRepository plans;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    private UUID family() {
        return jdbc.sql("insert into workspaces (name) values ('Familia Repo') returning id")
                .query(UUID.class)
                .single();
    }

    private UUID newOrder(UUID ws) {
        UUID by = users.create("Dono Repo", null);
        return orders.insert(ws, by, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);
    }

    @Test
    void markPaidWorksOnceAndAlsoForCanceledOrders() {
        UUID ws = family();
        UUID id = newOrder(ws);
        assertThat(orders.cancelOpen(ws)).isEqualTo(1);

        assertThat(orders.markPaid(id, PAID_AT)).isTrue();
        assertThat(orders.markPaid(id, PAID_AT)).isFalse();
        var row = orders.findForWorkspace(id, ws).orElseThrow();
        assertThat(row.status()).isEqualTo(OrderStatus.PAID);
        assertThat(row.paidAt()).isEqualTo(PAID_AT);
        assertThat(row.amountCents()).isEqualTo(8900L);
        assertThat(orders.findForWorkspace(id, family())).isEmpty();
    }

    @Test
    void lookupsByGatewayIds() {
        UUID ws = family();
        UUID id = newOrder(ws);
        orders.setCheckoutId(id, "chk_" + id);
        orders.setPaymentId(id, "pay_" + id);

        assertThat(orders.findByCheckoutId("chk_" + id))
                .hasValueSatisfying(r -> assertThat(r.id()).isEqualTo(id));
        assertThat(orders.findByPaymentId("pay_" + id))
                .hasValueSatisfying(r -> assertThat(r.id()).isEqualTo(id));
        assertThat(orders.findByPaymentId("pay_none")).isEmpty();
    }

    @Test
    void refundPendingNeedsPaidAndTransitionChecksTheFromStatus() {
        UUID ws = family();
        UUID id = newOrder(ws);

        assertThat(orders.markRefundPending(id, RefundReason.CUSTOMER, PAID_AT)).isFalse();
        orders.markPaid(id, PAID_AT);
        assertThat(orders.markRefundPending(id, RefundReason.CUSTOMER, PAID_AT)).isTrue();
        assertThat(orders.latestOpen(ws)).hasValueSatisfying(r -> {
            assertThat(r.status()).isEqualTo(OrderStatus.REFUND_PENDING);
            assertThat(r.refundReason()).isEqualTo(RefundReason.CUSTOMER);
        });
        assertThat(orders.transition(id, OrderStatus.PAID, OrderStatus.REFUNDED))
                .isFalse();
        assertThat(orders.transition(id, OrderStatus.REFUND_PENDING, OrderStatus.REFUNDED))
                .isTrue();
        assertThat(orders.latestOpen(ws)).isEmpty();
    }

    @Test
    void activateOnlyWhenFreeAndEndNow() {
        UUID ws = family();
        UUID first = newOrder(ws);
        orders.markPaid(first, PAID_AT);
        Instant ends = FounderOffer.endsAt(PAID_AT);

        assertThat(plans.activateIfFree(ws, first, PAID_AT, ends)).isTrue();
        plans.setTheme(ws, FounderTheme.PLUM);
        assertThat(plans.activateIfFree(ws, first, PAID_AT.plusSeconds(60), ends))
                .isFalse();

        plans.endNow(ws, first, PAID_AT.plusSeconds(120));
        var ended = plans.find(ws).orElseThrow();
        assertThat(ended.endsAt()).isEqualTo(PAID_AT.plusSeconds(120));
        assertThat(ended.theme()).isEqualTo("PLUM");

        UUID second = newOrder(ws);
        assertThat(plans.activateIfFree(ws, second, PAID_AT.plusSeconds(180), ends))
                .isTrue();
        var renewed = plans.find(ws).orElseThrow();
        assertThat(renewed.sourceOrderId()).isEqualTo(second);
        assertThat(renewed.theme()).isEqualTo("PLUM");
    }
}
