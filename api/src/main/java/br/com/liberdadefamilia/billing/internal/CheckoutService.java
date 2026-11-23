package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.CheckoutStarted;
import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.billing.domain.FounderOffer;
import br.com.liberdadefamilia.billing.domain.OrderStatus;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Starts the Founder checkout. The order is stored and committed first; the gateway is called after that, so no
 * database connection waits on the network. A failed call or a link outside the allowed hosts cancels the order.
 */
@Service
class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final BillingProperties properties;
    private final BillingStatus billingStatus;
    private final Plans plans;
    private final OrderRepository orders;
    private final PaymentGateway gateway;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final String webOrigin;

    CheckoutService(
            BillingProperties properties,
            BillingStatus billingStatus,
            Plans plans,
            OrderRepository orders,
            PaymentGateway gateway,
            ApplicationEventPublisher events,
            TransactionTemplate tx,
            @Value("${liberdade.web.origin}") String webOrigin) {
        this.properties = properties;
        this.billingStatus = billingStatus;
        this.plans = plans;
        this.orders = orders;
        this.gateway = gateway;
        this.events = events;
        this.tx = tx;
        this.webOrigin = webOrigin;
    }

    CheckoutView start(WorkspaceMember member) {
        if (!billingStatus.presaleOpen()) {
            throw BillingException.unavailable();
        }
        if (plans.current(member.workspaceId()).key() != PlanKey.FREE) {
            throw BillingException.planActive();
        }
        UUID orderId = openOrder(member);

        CheckoutSession session;
        try {
            session = gateway.createCheckout(new CheckoutRequest(orderId, webOrigin + "/mais/plano?pedido=" + orderId));
        } catch (RuntimeException e) {
            log.warn("checkout failed at the gateway ({})", e.getClass().getName());
            giveUp(orderId);
            throw BillingException.checkoutFailed();
        }
        if (!allowed(session.link())) {
            log.warn("checkout link is outside the allowed hosts");
            giveUp(orderId);
            throw BillingException.checkoutFailed();
        }
        tx.executeWithoutResult(status -> orders.setCheckoutId(orderId, session.checkoutId()));
        return new CheckoutView(orderId, session.link());
    }

    /**
     * Opens the order in its own transaction. Two tabs at once can both cancel the open order before either inserts,
     * so one insert meets the unique index of open orders; that one tries once more, which cancels the other tab's
     * order, and a second collision asks the person to try again.
     */
    private UUID openOrder(WorkspaceMember member) {
        try {
            return tx.execute(status -> open(member));
        } catch (DuplicateKeyException first) {
            try {
                return tx.execute(status -> open(member));
            } catch (DuplicateKeyException second) {
                log.warn("checkout collided twice with another open order");
                throw BillingException.checkoutFailed();
            }
        }
    }

    private UUID open(WorkspaceMember member) {
        orders.cancelOpen(member.workspaceId());
        UUID orderId = orders.insert(
                member.workspaceId(),
                member.userId(),
                FounderOffer.PRODUCT,
                FounderOffer.PRICE_CENTS,
                FounderOffer.CURRENCY);
        events.publishEvent(new CheckoutStarted(member.workspaceId(), orderId, member.userId()));
        return orderId;
    }

    private void giveUp(UUID orderId) {
        tx.executeWithoutResult(status -> orders.transition(orderId, OrderStatus.CREATED, OrderStatus.CANCELED));
    }

    /** Exactly {@code https} on its default port and exactly one of the configured hosts: no wildcard, no suffix match. */
    private boolean allowed(URI link) {
        String host = link.getHost();
        return "https".equals(link.getScheme())
                && (link.getPort() == -1 || link.getPort() == 443)
                && host != null
                && properties.checkoutHosts().stream()
                        .anyMatch(allowedHost -> allowedHost.strip().equalsIgnoreCase(host));
    }
}
