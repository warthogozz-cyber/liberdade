package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.RefundReason;
import br.com.liberdadefamilia.billing.domain.OrderStatus;
import br.com.liberdadefamilia.billing.domain.RefundWindow;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** The owner's 7-day refund: the rules that decide whether an order may be taken back. */
@Service
class RefundService {

    private final OrderRepository orders;
    private final PlanRepository plans;
    private final OrderPayments payments;
    private final Clock clock;

    RefundService(OrderRepository orders, PlanRepository plans, OrderPayments payments, Clock clock) {
        this.orders = orders;
        this.plans = plans;
        this.payments = payments;
        this.clock = clock;
    }

    /**
     * Refunds the order the family's running plan came from, while the 7 days are open. The plan ends at once; if the
     * gateway refuses, the order stays REFUND_PENDING for the retry job.
     */
    OrderView refund(UUID orderId, UUID workspaceId) {
        OrderRepository.OrderRow order =
                orders.findForWorkspace(orderId, workspaceId).orElseThrow(BillingException::orderNotFound);
        Instant now = clock.instant();
        if (!isOrigin(order, plans.find(workspaceId).orElse(null), now)) {
            throw BillingException.refundNotAllowed();
        }
        if (!RefundWindow.isOpen(order.paidAt(), now)) {
            throw BillingException.refundWindowClosed();
        }
        payments.requestRefund(orderId, RefundReason.CUSTOMER);
        OrderRepository.OrderRow after =
                orders.findForWorkspace(orderId, workspaceId).orElseThrow(BillingException::orderNotFound);
        return new OrderView(after.id(), after.status(), after.refundReason());
    }

    /** Whether the family may take its payment back now: the same rules the refund applies, window included. */
    boolean canRefund(UUID workspaceId) {
        Instant now = clock.instant();
        PlanRepository.PlanRow plan = plans.find(workspaceId).orElse(null);
        if (plan == null || plan.sourceOrderId() == null) {
            return false;
        }
        return orders.findForWorkspace(plan.sourceOrderId(), workspaceId)
                .filter(order -> isOrigin(order, plan, now) && RefundWindow.isOpen(order.paidAt(), now))
                .isPresent();
    }

    /** A paid order, not a duplicate, that the family's running plan came from. */
    private static boolean isOrigin(
            OrderRepository.OrderRow order, PlanRepository.@Nullable PlanRow plan, Instant now) {
        return order.status() == OrderStatus.PAID
                && order.refundReason() != RefundReason.DUPLICATE
                && order.paidAt() != null
                && plan != null
                && order.id().equals(plan.sourceOrderId())
                && plan.endsAt().isAfter(now);
    }
}
