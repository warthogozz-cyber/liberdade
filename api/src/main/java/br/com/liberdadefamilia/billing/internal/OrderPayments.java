package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.PlanActivated;
import br.com.liberdadefamilia.billing.PlanEnded;
import br.com.liberdadefamilia.billing.RefundReason;
import br.com.liberdadefamilia.billing.RefundRequested;
import br.com.liberdadefamilia.billing.domain.FounderOffer;
import br.com.liberdadefamilia.billing.domain.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What a confirmed payment does. {@link #applyPaid} is safe to call from the webhook and from the payer's return at
 * once: the order row decides which one acts. The gateway is only called after the transaction commits.
 */
@Service
class OrderPayments {

    private static final Logger log = LoggerFactory.getLogger(OrderPayments.class);

    /** What {@link #applyPaid} did. */
    enum ApplyResult {
        ACTIVATED,
        DUPLICATE_REFUND,
        ALREADY_APPLIED
    }

    private final OrderRepository orders;
    private final PlanRepository plans;
    private final PaymentGateway gateway;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    OrderPayments(
            OrderRepository orders,
            PlanRepository plans,
            PaymentGateway gateway,
            ApplicationEventPublisher events,
            TransactionTemplate tx,
            Clock clock) {
        this.orders = orders;
        this.plans = plans;
        this.gateway = gateway;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * Marks the order paid and gives the family the plan, unless it already has one running or no longer exists; then
     * the payment is refunded as a duplicate. The payment moment never counts as later than our own clock.
     */
    ApplyResult applyPaid(UUID orderId, Instant givenPaidAt, @Nullable String paymentId) {
        Instant now = clock.instant();
        Instant paidAt = givenPaidAt.isAfter(now) ? now : givenPaidAt;
        return tx.execute(status -> {
            if (!orders.markPaid(orderId, paidAt)) {
                return ApplyResult.ALREADY_APPLIED;
            }
            if (paymentId != null) {
                orders.setPaymentId(orderId, paymentId);
            }
            UUID ws = orders.findById(orderId)
                    .map(OrderRepository.OrderRow::workspaceId)
                    .orElse(null);
            if (ws != null) {
                Instant endsAt = FounderOffer.endsAt(paidAt);
                if (plans.activateIfFree(ws, orderId, paidAt, endsAt, now)) {
                    events.publishEvent(new PlanActivated(ws, orderId, endsAt));
                    return ApplyResult.ACTIVATED;
                }
            }
            requestRefund(orderId, RefundReason.DUPLICATE);
            return ApplyResult.DUPLICATE_REFUND;
        });
    }

    /**
     * Marks a paid order as waiting for its refund and, once that is committed, asks the gateway. If the gateway
     * fails the order stays REFUND_PENDING for the retry job.
     */
    void requestRefund(UUID orderId, RefundReason reason) {
        tx.executeWithoutResult(status -> {
            Optional<OrderRepository.OrderRow> found = orders.findById(orderId);
            if (found.isEmpty() || !orders.markRefundPending(orderId, reason, clock.instant())) {
                return;
            }
            OrderRepository.OrderRow order = found.get();
            if (reason == RefundReason.CUSTOMER && order.workspaceId() != null) {
                plans.endNow(order.workspaceId(), orderId, clock.instant());
                events.publishEvent(new PlanEnded(order.workspaceId(), orderId, "CUSTOMER_REFUND"));
            }
            events.publishEvent(new RefundRequested(order.workspaceId(), orderId, reason, null));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    callGatewayRefund(order.checkoutId(), order.paymentId());
                }
            });
        });
    }

    private void callGatewayRefund(@Nullable String checkoutId, @Nullable String knownPaymentId) {
        try {
            String paymentId = knownPaymentId;
            if (paymentId == null && checkoutId != null) {
                paymentId = gateway.findPayment(checkoutId)
                        .map(PaymentInfo::paymentId)
                        .orElse(null);
            }
            if (paymentId == null) {
                log.warn("refund waits for the retry job: no payment known yet");
                return;
            }
            gateway.refund(paymentId);
        } catch (RuntimeException failure) {
            log.warn("refund waits for the retry job: {}", failure.getClass().getName());
        }
    }

    /**
     * The payer's return: while the order waits for payment, asks the gateway and applies a confirmed payment. The
     * gateway is called outside any transaction; a gateway failure just leaves the order as it is.
     */
    OrderView refresh(UUID orderId, UUID workspaceId) {
        OrderRepository.OrderRow order =
                orders.findForWorkspace(orderId, workspaceId).orElseThrow(BillingException::orderNotFound);
        if (order.status() == OrderStatus.CREATED && order.checkoutId() != null) {
            try {
                gateway.findPayment(order.checkoutId())
                        .filter(payment -> "RECEIVED".equals(payment.status()) || "CONFIRMED".equals(payment.status()))
                        .ifPresent(payment -> applyPaid(
                                orderId,
                                payment.paidAt() != null ? payment.paidAt() : clock.instant(),
                                payment.paymentId()));
            } catch (PaymentGatewayException failure) {
                log.warn(
                        "refresh could not reach the gateway: {}",
                        failure.getClass().getName());
            }
            order = orders.findForWorkspace(orderId, workspaceId).orElseThrow(BillingException::orderNotFound);
        }
        return new OrderView(order.id(), order.status(), order.refundReason());
    }
}
