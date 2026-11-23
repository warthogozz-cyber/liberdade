package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.BillingExport;
import br.com.liberdadefamilia.billing.ExportedOrder;
import br.com.liberdadefamilia.billing.ExportedPlan;
import br.com.liberdadefamilia.billing.domain.FounderOffer;
import br.com.liberdadefamilia.billing.domain.OrderStatus;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

/** What billing hands over for the family's data export. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, BillingExportIT.Config.class})
class BillingExportIT {

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
    BillingExport export;

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

    @Test
    void exportsOrdersWithoutAsaasIds() {
        clock.freeze(BASE);
        UUID owner = users.create("Dono", null);
        UUID ws = workspaces.createFor(owner);
        UUID id = orders.insert(ws, owner, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);
        orders.setCheckoutId(id, "chk_fake_" + id);
        gateway.markPaid("chk_fake_" + id, "pay_" + id);
        payments.applyPaid(id, BASE, "pay_" + id);

        List<ExportedOrder> exported = export.orders(ws);

        assertThat(exported).hasSize(1);
        ExportedOrder order = exported.get(0);
        assertThat(order.product()).isEqualTo("FOUNDER_2026");
        assertThat(order.amountCents()).isEqualTo(8900);
        assertThat(order.currency()).isEqualTo("BRL");
        assertThat(order.status()).isEqualTo("PAID");
        assertThat(order.paidAt()).isEqualTo(BASE);
        assertThat(order.refundedAt()).isNull();
        Optional<ExportedPlan> plan = export.plan(ws);
        assertThat(plan).isPresent();
        assertThat(plan.get().key()).isEqualTo("FAMILY");
        assertThat(plan.get().founder()).isTrue();
        assertThat(plan.get().startsAt()).isEqualTo(BASE);
        assertThat(plan.get().endsAt()).isAfter(BASE.plus(Duration.ofDays(300)));
    }

    @Test
    void ordersComeInTheOrderTheyWereMade() {
        UUID owner = users.create("Dono", null);
        UUID ws = workspaces.createFor(owner);
        UUID first = orders.insert(ws, owner, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);
        orders.transition(first, OrderStatus.CREATED, OrderStatus.CANCELED);
        orders.insert(ws, owner, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);

        List<ExportedOrder> exported = export.orders(ws);

        assertThat(exported).extracting(ExportedOrder::status).containsExactly("CANCELED", "CREATED");
    }

    @Test
    void exportsOnlyTheOrdersAndThePlanOfTheFamilyAsked() {
        clock.freeze(BASE);
        UUID ownerA = users.create("Dono A", null);
        UUID wsA = workspaces.createFor(ownerA);
        UUID ownerB = users.create("Dono B", null);
        UUID wsB = workspaces.createFor(ownerB);
        UUID orderA = paid(wsA, ownerA);
        UUID orderB = paid(wsB, ownerB);
        orders.transition(orderB, OrderStatus.PAID, OrderStatus.REFUND_PENDING);

        assertThat(orderA).isNotEqualTo(orderB);
        assertThat(export.orders(wsA)).extracting(ExportedOrder::status).containsExactly("PAID");
        assertThat(export.orders(wsB)).extracting(ExportedOrder::status).containsExactly("REFUND_PENDING");
        assertThat(export.plan(wsA)).isPresent();
        assertThat(export.plan(wsB)).isPresent();
        UUID ownerC = users.create("Dono C", null);
        UUID wsC = workspaces.createFor(ownerC);
        assertThat(export.orders(wsC)).isEmpty();
        assertThat(export.plan(wsC)).isEmpty();
    }

    private UUID paid(UUID ws, UUID owner) {
        UUID id = orders.insert(ws, owner, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);
        orders.setCheckoutId(id, "chk_fake_" + id);
        gateway.markPaid("chk_fake_" + id, "pay_" + id);
        payments.applyPaid(id, BASE, "pay_" + id);
        return id;
    }

    @Test
    void planIsEmptyWhenFree() {
        UUID owner = users.create("Dono", null);
        UUID ws = workspaces.createFor(owner);

        assertThat(export.plan(ws)).isEmpty();
        assertThat(export.orders(ws)).isEmpty();
    }
}
