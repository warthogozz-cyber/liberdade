package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.RefundReason;
import br.com.liberdadefamilia.billing.domain.OrderStatus;
import br.com.liberdadefamilia.billing.domain.RefundWindow;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
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
        if (order.status() != OrderStatus.PAID
                || order.refundReason() == RefundReason.DUPLICATE
                || order.paidAt() == null
                || !originatesRunningPlan(orderId, workspaceId, now)) {
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

    private boolean originatesRunningPlan(UUID orderId, UUID workspaceId, Instant now) {
        return plans.find(workspaceId)
                .filter(plan ->
                        orderId.equals(plan.sourceOrderId()) && plan.endsAt().isAfter(now))
                .isPresent();
    }
}
