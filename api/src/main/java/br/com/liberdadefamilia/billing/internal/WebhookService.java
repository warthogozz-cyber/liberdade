package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.PlanEnded;
import br.com.liberdadefamilia.billing.domain.OrderStatus;
import br.com.liberdadefamilia.shared.DomainException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Records each Asaas event once and applies its effect. The effect runs in a savepoint: a rule that says no loses only
 * the effect, the event row stays with outcome ERROR and a person is alerted. A database error is not caught here.
 */
@Service
class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private static final Set<OrderStatus> REFUNDABLE = Set.of(OrderStatus.PAID, OrderStatus.REFUND_PENDING);

    /** What an event did to the order. */
    private enum Outcome {
        APPLIED,
        IGNORED,
        UNKNOWN_ORDER,
        ERROR
    }

    /** The result of the effect, and what is left to do once it is committed. */
    private record Effect(
            Outcome outcome,
            @Nullable UUID activatedOrder,
            @Nullable String checkoutId) {

        static Effect of(Outcome outcome) {
            return new Effect(outcome, null, null);
        }
    }

    private final JdbcClient jdbc;
    private final OrderRepository orders;
    private final PlanRepository plans;
    private final OrderPayments payments;
    private final BillingAlerts alerts;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final TransactionTemplate savepoint;
    private final Clock clock;

    WebhookService(
            JdbcClient jdbc,
            OrderRepository orders,
            PlanRepository plans,
            OrderPayments payments,
            BillingAlerts alerts,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactions,
            Clock clock) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.plans = plans;
        this.payments = payments;
        this.alerts = alerts;
        this.events = events;
        this.tx = new TransactionTemplate(transactions);
        this.savepoint = new TransactionTemplate(transactions);
        this.savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        this.clock = clock;
    }

    /** Records the event and applies it; a repeated event id does nothing. */
    void handle(AsaasEvent event) {
        Effect done = tx.execute(status -> record(event));
        if (done != null && done.activatedOrder() != null && done.checkoutId() != null) {
            // The commit is done: the gateway is asked outside the transaction, and a failure is left to the job.
            payments.resolvePaymentId(done.activatedOrder(), done.checkoutId());
        }
    }

    private @Nullable Effect record(AsaasEvent event) {
        Instant now = clock.instant();
        int inserted = jdbc.sql("""
                        insert into billing_webhook_events (event_id, event_type, checkout_id, payment_id, received_at)
                        values (:id, :type, :checkout, :payment, :now)
                        on conflict (event_id) do nothing""")
                .param("id", event.id())
                .param("type", event.event())
                .param("checkout", checkoutIdOf(event))
                .param(
                        "payment",
                        event.payment() == null ? null : event.payment().id())
                .param("now", Timestamp.from(now))
                .update();
        if (inserted == 0) {
            return null;
        }
        Effect effect;
        try {
            effect = savepoint.execute(status -> apply(event, now));
        } catch (DomainException | IllegalStateException failure) {
            log.warn("webhook event failed a rule: {}", failure.getClass().getName());
            alerts.raise(AlertType.WEBHOOK_ERROR);
            effect = Effect.of(Outcome.ERROR);
        }
        jdbc.sql("update billing_webhook_events set outcome = :outcome, processed_at = :now where event_id = :id")
                .param("outcome", effect.outcome().name())
                .param("now", Timestamp.from(clock.instant()))
                .param("id", event.id())
                .update();
        return effect;
    }

    private Effect apply(AsaasEvent event, Instant now) {
        return switch (event.event()) {
            case "CHECKOUT_PAID" -> checkoutPaid(event, now);
            case "CHECKOUT_EXPIRED" -> closeCheckout(event, OrderStatus.EXPIRED);
            case "CHECKOUT_CANCELED" -> closeCheckout(event, OrderStatus.CANCELED);
            case "PAYMENT_REFUNDED" -> paymentRefunded(event, now);
            case "PAYMENT_CHARGEBACK_REQUESTED", "PAYMENT_CHARGEBACK_DISPUTE" -> chargeback(event, now);
            default -> Effect.of(Outcome.IGNORED);
        };
    }

    private Effect checkoutPaid(AsaasEvent event, Instant now) {
        Optional<OrderRepository.OrderRow> order = byCheckout(event);
        if (order.isEmpty()) {
            return Effect.of(Outcome.UNKNOWN_ORDER);
        }
        UUID id = order.get().id();
        return switch (payments.applyPaid(id, now, null)) {
            case ACTIVATED -> new Effect(Outcome.APPLIED, id, order.get().checkoutId());
            case DUPLICATE_REFUND -> Effect.of(Outcome.APPLIED);
            case ALREADY_APPLIED -> Effect.of(Outcome.IGNORED);
        };
    }

    private Effect closeCheckout(AsaasEvent event, OrderStatus to) {
        Optional<OrderRepository.OrderRow> order = byCheckout(event);
        if (order.isEmpty()) {
            return Effect.of(Outcome.UNKNOWN_ORDER);
        }
        return Effect.of(
                orders.transition(order.get().id(), OrderStatus.CREATED, to) ? Outcome.APPLIED : Outcome.IGNORED);
    }

    private Effect paymentRefunded(AsaasEvent event, Instant now) {
        Optional<OrderRepository.OrderRow> found = byPayment(event);
        if (found.isEmpty()) {
            return Effect.of(Outcome.UNKNOWN_ORDER);
        }
        OrderRepository.OrderRow order = found.get();
        if (!REFUNDABLE.contains(order.status()) || !orders.markRefunded(order.id(), now)) {
            return Effect.of(Outcome.IGNORED);
        }
        endPlanIfOrigin(order, now, "REFUND");
        return Effect.of(Outcome.APPLIED);
    }

    private Effect chargeback(AsaasEvent event, Instant now) {
        Optional<OrderRepository.OrderRow> found = byPayment(event);
        if (found.isEmpty()) {
            return Effect.of(Outcome.UNKNOWN_ORDER);
        }
        OrderRepository.OrderRow order = found.get();
        if (!orders.markChargeback(order.id())) {
            return Effect.of(Outcome.IGNORED);
        }
        endPlanIfOrigin(order, now, "CHARGEBACK");
        alerts.raise(AlertType.CHARGEBACK);
        return Effect.of(Outcome.APPLIED);
    }

    /** Ends the family's plan when this order bought it and it is still running. */
    private void endPlanIfOrigin(OrderRepository.OrderRow order, Instant now, String reason) {
        UUID ws = order.workspaceId();
        if (ws == null) {
            return;
        }
        boolean running = plans.find(ws)
                .filter(plan -> order.id().equals(plan.sourceOrderId()))
                .filter(plan -> plan.endsAt().isAfter(now))
                .isPresent();
        if (running) {
            plans.endNow(ws, order.id(), now);
            events.publishEvent(new PlanEnded(ws, order.id(), reason));
        }
    }

    private static @Nullable String checkoutIdOf(AsaasEvent event) {
        if (event.checkout() != null) {
            return event.checkout().id();
        }
        return event.payment() == null ? null : event.payment().checkoutSession();
    }

    private Optional<OrderRepository.OrderRow> byCheckout(AsaasEvent event) {
        String checkoutId = event.checkout() == null ? null : event.checkout().id();
        return checkoutId == null ? Optional.empty() : orders.findByCheckoutId(checkoutId);
    }

    /** By the payment id, else by the checkout the payment came from. */
    private Optional<OrderRepository.OrderRow> byPayment(AsaasEvent event) {
        AsaasEvent.Payment payment = event.payment();
        if (payment == null) {
            return Optional.empty();
        }
        Optional<OrderRepository.OrderRow> order =
                payment.id() == null ? Optional.empty() : orders.findByPaymentId(payment.id());
        if (order.isEmpty() && payment.checkoutSession() != null) {
            order = orders.findByCheckoutId(payment.checkoutSession());
        }
        return order;
    }
}
