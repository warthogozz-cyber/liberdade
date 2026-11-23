package br.com.liberdadefamilia.billing.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.RequiresPlan;
import br.com.liberdadefamilia.shared.RateLimitedException;
import br.com.liberdadefamilia.shared.RateLimiter;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** What the family's owner does about the paid plan. */
@RestController
class BillingController {

    private final CheckoutService checkout;
    private final OrderPayments payments;
    private final RefundService refunds;
    private final PlanDetailsService planDetails;
    private final RateLimiter checkoutLimiter;
    private final RateLimiter refreshLimiter;
    private final RateLimiter refundLimiter;

    BillingController(
            CheckoutService checkout,
            OrderPayments payments,
            RefundService refunds,
            PlanDetailsService planDetails,
            @Qualifier("checkoutRateLimiter") RateLimiter checkoutLimiter,
            @Qualifier("refreshRateLimiter") RateLimiter refreshLimiter,
            @Qualifier("refundRateLimiter") RateLimiter refundLimiter) {
        this.checkout = checkout;
        this.payments = payments;
        this.refunds = refunds;
        this.planDetails = planDetails;
        this.checkoutLimiter = checkoutLimiter;
        this.refreshLimiter = refreshLimiter;
        this.refundLimiter = refundLimiter;
    }

    /** Opens the Founder checkout; the payer is sent to the returned page. */
    @PostMapping("/v1/workspaces/{ws}/billing/checkout")
    @WorkspaceScoped(allow = {OWNER})
    @ResponseStatus(HttpStatus.CREATED)
    CheckoutView startCheckout(WorkspaceMember member) {
        String key = member.workspaceId().toString();
        if (!checkoutLimiter.tryAcquire(key)) {
            throw new RateLimitedException(checkoutLimiter.retryAfterSeconds(key));
        }
        return checkout.start(member);
    }

    /** The payer is back from the checkout: looks at the order again and applies a payment that is confirmed. */
    @PostMapping("/v1/workspaces/{ws}/billing/orders/{id}/refresh")
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    OrderView refresh(WorkspaceMember member, @PathVariable UUID id) {
        String key = member.workspaceId().toString();
        if (!refreshLimiter.tryAcquire(key)) {
            throw new RateLimitedException(refreshLimiter.retryAfterSeconds(key));
        }
        return payments.refresh(id, member.workspaceId());
    }

    /** The owner takes the payment back within 7 days: the plan ends now and the refund goes to the gateway. */
    @PostMapping("/v1/workspaces/{ws}/billing/orders/{id}/refund")
    @WorkspaceScoped(allow = {OWNER})
    @ResponseStatus(HttpStatus.ACCEPTED)
    OrderView refund(WorkspaceMember member, @PathVariable UUID id) {
        String key = member.workspaceId().toString();
        if (!refundLimiter.tryAcquire(key)) {
            throw new RateLimitedException(refundLimiter.retryAfterSeconds(key));
        }
        return refunds.refund(id, member.workspaceId(), member.userId());
    }

    /** The family's plan and what the caller may do about it. */
    @GetMapping("/v1/workspaces/{ws}/billing/plan")
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    PlanDetails plan(WorkspaceMember member) {
        return planDetails.details(member);
    }

    /** The owner picks the Founder theme; it needs the family plan. */
    @PutMapping("/v1/workspaces/{ws}/billing/plan/theme")
    @WorkspaceScoped(allow = {OWNER})
    @RequiresPlan(PlanKey.FAMILY)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setTheme(WorkspaceMember member, @Valid @RequestBody ThemeRequest request) {
        planDetails.setTheme(member.workspaceId(), request.theme());
    }
}
