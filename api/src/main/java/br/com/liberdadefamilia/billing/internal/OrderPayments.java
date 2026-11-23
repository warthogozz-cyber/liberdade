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
import org.springframework.transaction.TransactionDefinition;
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
    /** For work that runs after a commit, where the finished transaction is still bound to the thread. */
    private final TransactionTemplate own;

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
        this.own = new TransactionTemplate(tx.getTransactionManager());
        this.own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /**
     * Marks the order paid and gives the family the plan, unless it already has one running or no longer exists; then
     * the payment is refunded as a duplicate. The payment moment never counts as later than our own clock.
     */
    ApplyResult applyPaid(UUID orderId, Instant givenPaidAt, @Nullable String paymentId) {
        return tx.execute(status -> {
            Instant now = clock.instant();
            Instant paidAt = givenPaidAt.isAfter(now) ? now : givenPaidAt;
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
            requestRefund(orderId, RefundReason.DUPLICATE, null);
            return ApplyResult.DUPLICATE_REFUND;
        });
    }

    /**
     * Marks a paid order as waiting for its refund and, once that is committed, asks the gateway. If the gateway
     * fails the order stays REFUND_PENDING for the retry job.
     *
     * @param actorUserId who asked for it, for the audit trail; null when the system refunds on its own (DUPLICATE)
     */
    void requestRefund(UUID orderId, RefundReason reason, @Nullable UUID actorUserId) {
        tx.executeWithoutResult(status -> {
            Instant now = clock.instant();
            Optional<OrderRepository.OrderRow> found = orders.findById(orderId);
            if (found.isEmpty() || !orders.markRefundPending(orderId, reason, now)) {
                return;
            }
            OrderRepository.OrderRow order = found.get();
            if (reason == RefundReason.CUSTOMER && order.workspaceId() != null) {
                plans.endNow(order.workspaceId(), orderId, now);
                events.publishEvent(new PlanEnded(order.workspaceId(), orderId, "CUSTOMER_REFUND"));
            }
            events.publishEvent(new RefundRequested(order.workspaceId(), orderId, reason, actorUserId));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    callGatewayRefund(orderId);
                }
            });
        });
    }

    /**
     * Asks the gateway for the refund, after the commit. The order is read again first: the synchronization may have
     * been registered inside a savepoint that rolled back while the outer transaction still committed, and then the
     * order is not REFUND_PENDING and nothing is sent.
     */
    private void callGatewayRefund(UUID orderId) {
        try {
            OrderRepository.OrderRow order = own.execute(status -> orders.findById(orderId)
                    .filter(row -> row.status() == OrderStatus.REFUND_PENDING)
                    .orElse(null));
            if (order == null) {
                return;
            }
            String paymentId = order.paymentId();
            if (paymentId == null && order.checkoutId() != null) {
                paymentId = resolvePaymentId(orderId, order.checkoutId());
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
     * Asks the gateway which payment the checkout got and keeps it on the order, unless the order already has one.
     * Meant for after a commit: it runs in a transaction of its own. A failure is logged and swallowed, because the
     * hourly job asks again. Returns the payment id, or null when it is not known.
     */
    @Nullable
    String resolvePaymentId(UUID orderId, String checkoutId) {
        try {
            Optional<PaymentInfo> payment = gateway.findPayment(checkoutId);
            if (payment.isEmpty()) {
                return null;
            }
            String paymentId = payment.get().paymentId();
            own.executeWithoutResult(status -> orders.setPaymentIdIfMissing(orderId, paymentId));
            return paymentId;
        } catch (RuntimeException failure) {
            log.warn(
                    "payment id waits for the retry job: {}", failure.getClass().getName());
            return null;
        }
    }

    /**
     * The payer's return: while the order waits for payment, asks the gateway and applies a confirmed payment. The
     * payment counts from now, the moment we saw it confirmed: the gateway only reports the day, never the time. The
     * gateway is called outside any transaction; a failure there just leaves the order as it is.
     */
    OrderView refresh(UUID orderId, UUID workspaceId) {
        OrderRepository.OrderRow order =
                orders.findForWorkspace(orderId, workspaceId).orElseThrow(BillingException::orderNotFound);
        if (order.status() == OrderStatus.CREATED && order.checkoutId() != null) {
            Optional<PaymentInfo> payment;
            try {
                payment = gateway.findPayment(order.checkoutId());
            } catch (RuntimeException failure) {
                log.warn(
                        "refresh could not reach the gateway: {}",
                        failure.getClass().getName());
                payment = Optional.empty();
            }
            payment.filter(OrderPayments::confirmed)
                    .ifPresent(found -> applyPaid(orderId, clock.instant(), found.paymentId()));
            order = orders.findForWorkspace(orderId, workspaceId).orElseThrow(BillingException::orderNotFound);
        }
        return new OrderView(order.id(), order.status(), order.refundReason());
    }

    /** Whether the gateway reports the payment as made: RECEIVED (Pix) or CONFIRMED (card). */
    static boolean confirmed(PaymentInfo payment) {
        return "RECEIVED".equals(payment.status()) || "CONFIRMED".equals(payment.status());
    }
}
