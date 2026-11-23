package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.domain.FounderTheme;
import br.com.liberdadefamilia.billing.domain.RefundWindow;
import br.com.liberdadefamilia.workspace.Role;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Works out what the plan page shows and what the caller may do about the plan (D6, D7). */
@Service
class PlanDetailsService {

    private final PlanRepository plans;
    private final OrderRepository orders;
    private final RefundService refunds;
    private final BillingStatus status;
    private final Clock clock;

    PlanDetailsService(
            PlanRepository plans, OrderRepository orders, RefundService refunds, BillingStatus status, Clock clock) {
        this.plans = plans;
        this.orders = orders;
        this.refunds = refunds;
        this.status = status;
        this.clock = clock;
    }

    PlanDetails details(WorkspaceMember member) {
        UUID ws = member.workspaceId();
        Instant now = clock.instant();
        var open = orders.latestOpen(ws).map(o -> new OrderView(o.id(), o.status(), o.refundReason()));
        boolean presaleOpen = status.presaleOpen();
        var running = plans.find(ws).filter(plan -> plan.endsAt().isAfter(now));
        if (running.isEmpty()) {
            boolean canPurchase = presaleOpen && member.role() == Role.OWNER;
            return new PlanDetails(
                    PlanKey.FREE,
                    false,
                    null,
                    FounderTheme.TEAL,
                    presaleOpen,
                    canPurchase,
                    false,
                    null,
                    null,
                    open.orElse(null));
        }
        PlanRepository.PlanRow plan = running.get();
        Instant deadline = plan.sourceOrderId() == null
                ? null
                : orders.findForWorkspace(plan.sourceOrderId(), ws)
                        .map(OrderRepository.OrderRow::paidAt)
                        .map(RefundWindow::deadline)
                        .orElse(null);
        boolean canRefund = member.role() == Role.OWNER && refunds.canRefund(ws);
        return new PlanDetails(
                PlanKey.FAMILY,
                plan.founder(),
                plan.endsAt(),
                FounderTheme.orDefault(plan.theme()),
                presaleOpen,
                false,
                canRefund,
                deadline,
                plan.sourceOrderId(),
                open.orElse(null));
    }

    void setTheme(UUID ws, FounderTheme theme) {
        plans.setTheme(ws, theme);
    }
}
