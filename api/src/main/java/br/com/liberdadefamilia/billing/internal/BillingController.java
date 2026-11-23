package br.com.liberdadefamilia.billing.internal;

import static br.com.liberdadefamilia.workspace.Role.OWNER;

import br.com.liberdadefamilia.shared.RateLimitedException;
import br.com.liberdadefamilia.shared.RateLimiter;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** What the family's owner does about the paid plan. */
@RestController
class BillingController {

    private final CheckoutService checkout;
    private final RateLimiter checkoutLimiter;

    BillingController(CheckoutService checkout, @Qualifier("checkoutRateLimiter") RateLimiter checkoutLimiter) {
        this.checkout = checkout;
        this.checkoutLimiter = checkoutLimiter;
    }

    /** Opens the Founder checkout; the payer is sent to the returned page. */
    @PostMapping("/v1/workspaces/{ws}/billing/checkout")
    @WorkspaceScoped(allow = {OWNER})
    @ResponseStatus(HttpStatus.CREATED)
    CheckoutView startCheckout(WorkspaceMember member) {
        String key = member.userId().toString();
        if (!checkoutLimiter.tryAcquire(key)) {
            throw new RateLimitedException(checkoutLimiter.retryAfterSeconds(key));
        }
        return checkout.start(member);
    }
}
