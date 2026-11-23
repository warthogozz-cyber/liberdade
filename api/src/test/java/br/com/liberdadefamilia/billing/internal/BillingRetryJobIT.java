package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.billing.RefundReason;
import br.com.liberdadefamilia.billing.domain.FounderOffer;
import br.com.liberdadefamilia.billing.domain.OrderStatus;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Every hour the job asks again about refunds the gateway refused and payments whose id is not known yet. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import({TestcontainersConfiguration.class, BillingRetryJobIT.Config.class})
class BillingRetryJobIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    private static final Instant BASE = Instant.parse("2027-01-10T12:00:00Z");

    @Autowired
    BillingRetryJob job;

    @Autowired
    OrderRepository orders;

    @Autowired
    OrderPayments payments;

    @MockitoSpyBean
    FakePaymentGateway gateway;

    @Autowired
    Plans plans;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestClock clock;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void freeze() {
        clock.freeze(BASE);
    }

    @AfterEach
    void releaseClock() {
        clock.release();
        Mockito.reset(gateway);
    }

    private record Order(UUID id, UUID ws, String checkout, String payment) {}

    /** A paid order whose family has its plan. */
    private Order paidOrder(boolean knownPaymentId) {
        UUID owner = users.create("Dono Job", null);
        UUID ws = workspaces.createFor(owner);
        UUID id = orders.insert(ws, owner, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);
        String checkout = "chk_job_" + id;
        String payment = "pay_job_" + id;
        orders.setCheckoutId(id, checkout);
        gateway.markPaid(checkout, payment);
        payments.applyPaid(id, BASE, knownPaymentId ? payment : null);
        return new Order(id, ws, checkout, payment);
    }

    /** A paid order whose refund the gateway refused once. */
    private Order refusedRefund(RefundReason reason) {
        Order order = paidOrder(true);
        gateway.failNextRefund();
        payments.requestRefund(order.id, reason, null);
        assertThat(status(order)).isEqualTo(OrderStatus.REFUND_PENDING);
        assertThat(gatewayStatus(order)).isEqualTo("RECEIVED");
        return order;
    }

    private OrderStatus status(Order order) {
        return orders.findById(order.id).orElseThrow().status();
    }

    private String gatewayStatus(Order order) {
        return gateway.findPayment(order.checkout).orElseThrow().status();
    }

    @Test
    void retriesRefusedRefund() {
        Order order = refusedRefund(RefundReason.CUSTOMER);

        job.run();

        assertThat(gatewayStatus(order)).isEqualTo("REFUNDED");
    }

    @Test
    void marksRefundedWhenAsaasSaysSo() {
        Order order = refusedRefund(RefundReason.CUSTOMER);
        gateway.markRefunded(order.payment);

        job.run();

        var row = orders.findById(order.id).orElseThrow();
        assertThat(row.status()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(row.refundedAt()).isEqualTo(BASE);
    }

    @Test
    void duplicateRefundKeepsTheRunningPlan() {
        Order order = refusedRefund(RefundReason.DUPLICATE);
        gateway.markRefunded(order.payment);

        job.run();

        assertThat(status(order)).isEqualTo(OrderStatus.REFUNDED);
        assertThat(plans.current(order.ws).key()).isEqualTo(PlanKey.FAMILY);
    }

    @Test
    void waitsWhileAsaasIsRefunding() {
        Order order = refusedRefund(RefundReason.CUSTOMER);
        Mockito.clearInvocations(gateway);
        doReturn(Optional.of(new PaymentInfo(order.payment, "REFUND_IN_PROGRESS")))
                .when(gateway)
                .findPayment(order.checkout);

        job.run();

        verify(gateway, never()).refund(order.payment);
        assertThat(status(order)).isEqualTo(OrderStatus.REFUND_PENDING);
    }

    @Test
    void stopsAfter72Hours() {
        Order order = refusedRefund(RefundReason.CUSTOMER);
        clock.freeze(BASE.plus(Duration.ofHours(72)).plusSeconds(1));

        job.run();

        assertThat(gatewayStatus(order)).isEqualTo("RECEIVED");
        assertThat(status(order)).isEqualTo(OrderStatus.REFUND_PENDING);
    }

    @Test
    void exactly72HoursIsExcluded() {
        Order order = refusedRefund(RefundReason.CUSTOMER);
        clock.freeze(BASE.plus(Duration.ofHours(72)));

        job.run();

        assertThat(gatewayStatus(order)).isEqualTo("RECEIVED");
    }

    @Test
    void resolvesMissingPaymentId() {
        Order order = paidOrder(false);
        assertThat(orders.findById(order.id).orElseThrow().paymentId()).isNull();

        job.run();

        assertThat(orders.findById(order.id).orElseThrow().paymentId()).isEqualTo(order.payment);
    }

    @Test
    void failureLogsOnlyTheClass(CapturedOutput output) {
        Order order = refusedRefund(RefundReason.CUSTOMER);
        doThrow(new PaymentGatewayException(503)).when(gateway).findPayment(eq(order.checkout));

        int before = output.getAll().length();

        job.run();

        String fromTheJob = output.getAll().substring(before);
        assertThat(fromTheJob)
                .contains("billing retry: refund step failed (" + PaymentGatewayException.class.getName() + ")")
                .doesNotContain("asaas status")
                .doesNotContain(order.checkout)
                .doesNotContain(order.payment);
    }

    @Test
    void completesMissingPaymentIdOfRefundPending() {
        UUID owner = users.create("Dono Job", null);
        UUID ws = workspaces.createFor(owner);
        UUID id = orders.insert(ws, owner, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);
        String checkout = "chk_job_" + id;
        orders.setCheckoutId(id, checkout);
        gateway.markPaid(checkout, "pay_job_" + id);
        payments.applyPaid(id, BASE, null);
        // The lookup fails once while the refund is requested, so the order waits with no payment id.
        doThrow(new PaymentGatewayException(503))
                .doCallRealMethod()
                .when(gateway)
                .findPayment(eq(checkout));
        payments.requestRefund(id, RefundReason.CUSTOMER, null);
        assertThat(orders.findById(id).orElseThrow().paymentId()).isNull();

        job.run();

        assertThat(orders.findById(id).orElseThrow().paymentId()).isEqualTo("pay_job_" + id);
        assertThat(gateway.findPayment(checkout).orElseThrow().status()).isEqualTo("REFUNDED");
    }

    @Test
    void oneFailureDoesNotStopTheOthers() {
        Order first = refusedRefund(RefundReason.CUSTOMER);
        clock.freeze(BASE.plusSeconds(1));
        Order second = refusedRefund(RefundReason.CUSTOMER);
        doThrow(new PaymentGatewayException(500)).when(gateway).findPayment(eq(first.checkout));

        job.run();
        Mockito.reset(gateway);

        assertThat(gatewayStatus(second)).isEqualTo("REFUNDED");
        assertThat(gatewayStatus(first)).isEqualTo("RECEIVED");
    }

    /** A checkout the family paid at Asaas whose webhook never arrived; the row was created at {@code createdAt}. */
    private Order unconfirmedPayment(OrderStatus status, Instant createdAt) {
        UUID owner = users.create("Dono Job", null);
        UUID ws = workspaces.createFor(owner);
        UUID id = orders.insert(ws, owner, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);
        String checkout = "chk_job_" + id;
        String payment = "pay_job_" + id;
        orders.setCheckoutId(id, checkout);
        if (status != OrderStatus.CREATED) {
            orders.transition(id, OrderStatus.CREATED, status);
        }
        jdbc.sql("update billing_orders set created_at = :at where id = :id")
                .param("at", Timestamp.from(createdAt))
                .param("id", id)
                .update();
        gateway.markPaid(checkout, payment);
        return new Order(id, ws, checkout, payment);
    }

    @Test
    void reconcilesAPaymentWhoseWebhookWasLost() {
        Order order = unconfirmedPayment(OrderStatus.CREATED, BASE.minus(Duration.ofHours(1)));

        job.run();

        var row = orders.findById(order.id).orElseThrow();
        assertThat(row.status()).isEqualTo(OrderStatus.PAID);
        assertThat(row.paidAt()).isEqualTo(BASE);
        assertThat(row.paymentId()).isEqualTo(order.payment);
        assertThat(plans.current(order.ws).key()).isEqualTo(PlanKey.FAMILY);
    }

    @Test
    void reconcilesACanceledCheckoutThatWasPaid() {
        Order order = unconfirmedPayment(OrderStatus.CANCELED, BASE.minus(Duration.ofHours(47)));

        job.run();

        assertThat(status(order)).isEqualTo(OrderStatus.PAID);
        assertThat(plans.current(order.ws).key()).isEqualTo(PlanKey.FAMILY);
    }

    @Test
    void reconcileLeavesCheckoutsOlderThan48Hours() {
        Order order = unconfirmedPayment(OrderStatus.CREATED, BASE.minus(Duration.ofHours(48)));

        job.run();

        assertThat(status(order)).isEqualTo(OrderStatus.CREATED);
        verify(gateway, never()).findPayment(order.checkout);
    }

    @Test
    void reconcileLeavesUnpaidCheckouts() {
        Order order = unconfirmedPayment(OrderStatus.CREATED, BASE.minus(Duration.ofHours(1)));
        doReturn(Optional.of(new PaymentInfo(order.payment, "PENDING")))
                .when(gateway)
                .findPayment(order.checkout);

        job.run();

        assertThat(status(order)).isEqualTo(OrderStatus.CREATED);
        assertThat(plans.current(order.ws).key()).isEqualTo(PlanKey.FREE);
    }

    @Test
    void oneReconcileFailureDoesNotStopTheOthers(CapturedOutput output) {
        Order first = unconfirmedPayment(OrderStatus.CREATED, BASE.minus(Duration.ofHours(2)));
        Order second = unconfirmedPayment(OrderStatus.CREATED, BASE.minus(Duration.ofHours(1)));
        doThrow(new PaymentGatewayException(503)).when(gateway).findPayment(eq(first.checkout));

        job.run();

        assertThat(status(first)).isEqualTo(OrderStatus.CREATED);
        assertThat(status(second)).isEqualTo(OrderStatus.PAID);
        assertThat(output.getAll())
                .contains("billing retry: reconcile step failed (" + PaymentGatewayException.class.getName() + ")")
                .doesNotContain(first.checkout);
    }

    @Test
    void aRefundFinishedMeanwhileIsNotSentAgain() {
        Order order = refusedRefund(RefundReason.CUSTOMER);
        Mockito.clearInvocations(gateway);
        // The webhook marks the order refunded while the job is asking the gateway.
        doAnswer(invocation -> {
                    orders.markRefunded(order.id, BASE);
                    return Optional.of(new PaymentInfo(order.payment, "RECEIVED"));
                })
                .when(gateway)
                .findPayment(order.checkout);

        job.run();

        verify(gateway, never()).refund(order.payment);
        assertThat(status(order)).isEqualTo(OrderStatus.REFUNDED);
    }
}
