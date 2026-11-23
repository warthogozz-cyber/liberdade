package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.domain.OrderStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Every hour, asks the gateway again about refunds it had not finished (up to 72 hours after the request), finds the
 * payment id of paid orders that still have none, and looks for payments the webhook never reported: an unpaid order
 * whose checkout is less than 48 hours old and that the gateway says was paid is applied as if the webhook had come
 * now. The gateway is called outside any transaction; each write is
 * a short transaction of its own, and one order's failure does not stop the others. Failures log the exception class
 * only: the message could carry the gateway's answer.
 */
@Component
class BillingRetryJob {

    private static final Logger log = LoggerFactory.getLogger(BillingRetryJob.class);

    private static final Duration REFUND_RETRY_WINDOW = Duration.ofHours(72);

    private static final Duration RECONCILE_WINDOW = Duration.ofHours(48);

    private final OrderRepository orders;
    private final PaymentGateway gateway;
    private final OrderPayments payments;
    private final Clock clock;
    private final TransactionTemplate tx;

    BillingRetryJob(
            OrderRepository orders,
            PaymentGateway gateway,
            OrderPayments payments,
            Clock clock,
            PlatformTransactionManager transactions) {
        this.orders = orders;
        this.gateway = gateway;
        this.payments = payments;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    @Scheduled(cron = "0 5 * * * *", zone = "America/Fortaleza")
    public void run() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(REFUND_RETRY_WINDOW);
        int refunds = 0;
        for (OrderRepository.OrderRow order : orders.refundPendingSince(cutoff)) {
            try {
                retryRefund(order, now);
                refunds++;
            } catch (RuntimeException failure) {
                log.warn(
                        "billing retry: refund step failed ({})",
                        failure.getClass().getName());
            }
        }
        int resolved = 0;
        for (OrderRepository.OrderRow order : orders.paidWithoutPaymentId()) {
            try {
                if (payments.resolvePaymentId(order.id(), order.checkoutId()) != null) {
                    resolved++;
                }
            } catch (RuntimeException failure) {
                log.warn(
                        "billing retry: payment id step failed ({})",
                        failure.getClass().getName());
            }
        }
        int reconciled = 0;
        for (OrderRepository.OrderRow order : orders.unconfirmedCheckoutsSince(now.minus(RECONCILE_WINDOW))) {
            try {
                if (reconcile(order)) {
                    reconciled++;
                }
            } catch (RuntimeException failure) {
                log.warn(
                        "billing retry: reconcile step failed ({})",
                        failure.getClass().getName());
            }
        }
        log.info(
                "Billing retry: {} refunds looked at, {} payment ids found, {} payments reconciled",
                refunds,
                resolved,
                reconciled);
    }

    /** Applies a payment the gateway confirms but we never heard of, dated now as the webhook would be. */
    private boolean reconcile(OrderRepository.OrderRow order) {
        Optional<PaymentInfo> found = gateway.findPayment(order.checkoutId());
        if (found.isEmpty() || !OrderPayments.confirmed(found.get())) {
            return false;
        }
        payments.applyPaid(order.id(), clock.instant(), found.get().paymentId());
        return true;
    }

    /** Looks at the payment and finishes, waits for or repeats the refund. Only the order changes, never the plan. */
    private void retryRefund(OrderRepository.OrderRow order, Instant now) {
        if (order.checkoutId() == null) {
            return;
        }
        Optional<PaymentInfo> found = gateway.findPayment(order.checkoutId());
        if (found.isEmpty()) {
            return;
        }
        PaymentInfo payment = found.get();
        if (order.paymentId() == null) {
            tx.executeWithoutResult(status -> orders.setPaymentIdIfMissing(order.id(), payment.paymentId()));
        }
        switch (payment.status()) {
            case "REFUNDED" -> tx.executeWithoutResult(status -> orders.markRefunded(order.id(), now));
            case "REFUND_REQUESTED", "REFUND_IN_PROGRESS" -> {
                // Asaas is already on it.
            }
            default -> {
                // The webhook may have finished the order since the list was read.
                if (stillPendingRefund(order)) {
                    gateway.refund(payment.paymentId());
                }
            }
        }
    }

    private boolean stillPendingRefund(OrderRepository.OrderRow order) {
        return orders.findById(order.id())
                .map(current -> current.status() == OrderStatus.REFUND_PENDING)
                .orElse(false);
    }
}
