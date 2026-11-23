package br.com.liberdadefamilia.billing.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.shared.RateLimitedException;
import br.com.liberdadefamilia.shared.RateLimiter;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** What the family's owner does about the paid plan. */
@RestController
class BillingController {

    private final CheckoutService checkout;
    private final OrderPayments payments;
    private final RateLimiter checkoutLimiter;
    private final RateLimiter refreshLimiter;

    BillingController(
            CheckoutService checkout,
            OrderPayments payments,
            @Qualifier("checkoutRateLimiter") RateLimiter checkoutLimiter,
            @Qualifier("refreshRateLimiter") RateLimiter refreshLimiter) {
        this.checkout = checkout;
        this.payments = payments;
        this.checkoutLimiter = checkoutLimiter;
        this.refreshLimiter = refreshLimiter;
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
}
