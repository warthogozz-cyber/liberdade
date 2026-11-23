package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.RefundReason;
import br.com.liberdadefamilia.billing.domain.OrderStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The billing_orders table. */
@Component
class OrderRepository {

    /** One stored order. */
    record OrderRow(
            UUID id,
            @Nullable UUID workspaceId,
            @Nullable UUID requestedBy,
            String product,
            long amountCents,
            String currency,
            OrderStatus status,
            @Nullable String checkoutId,
            @Nullable String paymentId,
            @Nullable Instant paidAt,
            @Nullable Instant refundRequestedAt,
            @Nullable Instant refundedAt,
            @Nullable RefundReason refundReason,
            Instant createdAt) {}

    private static final String COLUMNS = """
            id, workspace_id, requested_by, product, amount_cents, currency, status, asaas_checkout_id,
            asaas_payment_id, paid_at, refund_requested_at, refunded_at, refund_reason, created_at""";

    private final JdbcClient jdbc;

    OrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Starts an order in CREATED and returns its id. */
    UUID insert(UUID ws, UUID requestedBy, String product, long amountCents, String currency) {
        return jdbc.sql("""
                        insert into billing_orders (workspace_id, requested_by, product, amount_cents, currency, status)
                        values (:ws, :by, :product, :amount, :currency, 'CREATED')
                        returning id""")
                .param("ws", ws)
                .param("by", requestedBy)
                .param("product", product)
                .param("amount", amountCents)
                .param("currency", currency)
                .query(UUID.class)
                .single();
    }

    Optional<OrderRow> findById(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from billing_orders where id = :id")
                .param("id", id)
                .query(OrderRepository::map)
                .optional();
    }

    Optional<OrderRow> findForWorkspace(UUID id, UUID ws) {
        return jdbc.sql("select " + COLUMNS + " from billing_orders where id = :id and workspace_id = :ws")
                .param("id", id)
                .param("ws", ws)
                .query(OrderRepository::map)
                .optional();
    }

    Optional<OrderRow> findByCheckoutId(String checkoutId) {
        return jdbc.sql("select " + COLUMNS + " from billing_orders where asaas_checkout_id = :id")
                .param("id", checkoutId)
                .query(OrderRepository::map)
                .optional();
    }

    Optional<OrderRow> findByPaymentId(String paymentId) {
        return jdbc.sql("select " + COLUMNS + " from billing_orders where asaas_payment_id = :id")
                .param("id", paymentId)
                .query(OrderRepository::map)
                .optional();
    }

    /** Marks the order PAID if it still can be; false means it was already paid or went another way. */
    boolean markPaid(UUID id, Instant paidAt) {
        return jdbc.sql("""
                                update billing_orders set status = 'PAID', paid_at = :paidAt, updated_at = now()
                                where id = :id and status in ('CREATED','EXPIRED','CANCELED')""")
                        .param("id", id)
                        .param("paidAt", Timestamp.from(paidAt))
                        .update()
                > 0;
    }

    /** Moves the order from one status to the next only if it is still in the first. */
    boolean transition(UUID id, OrderStatus from, OrderStatus to) {
        return jdbc.sql("update billing_orders set status = :to, updated_at = now() where id = :id and status = :from")
                        .param("id", id)
                        .param("from", from.name())
                        .param("to", to.name())
                        .update()
                > 0;
    }

    /** Cancels the family's open order on our side and returns how many there were (none or one). */
    int cancelOpen(UUID ws) {
        return jdbc.sql("""
                        update billing_orders set status = 'CANCELED', updated_at = now()
                        where workspace_id = :ws and status = 'CREATED'""").param("ws", ws).update();
    }

    void setCheckoutId(UUID id, String checkoutId) {
        jdbc.sql("update billing_orders set asaas_checkout_id = :value, updated_at = now() where id = :id")
                .param("id", id)
                .param("value", checkoutId)
                .update();
    }

    void setPaymentId(UUID id, String paymentId) {
        jdbc.sql("update billing_orders set asaas_payment_id = :value, updated_at = now() where id = :id")
                .param("id", id)
                .param("value", paymentId)
                .update();
    }

    /** Stores the payment id unless one is already there; false when it was. */
    boolean setPaymentIdIfMissing(UUID id, String paymentId) {
        return jdbc.sql("""
                        update billing_orders set asaas_payment_id = :value, updated_at = now()
                        where id = :id and asaas_payment_id is null""").param("id", id).param("value", paymentId).update() > 0;
    }

    /** PAID or REFUND_PENDING to REFUNDED at the given moment; false when the order is in any other status. */
    boolean markRefunded(UUID id, Instant at) {
        return jdbc.sql("""
                        update billing_orders set status = 'REFUNDED', refunded_at = :at, updated_at = now()
                        where id = :id and status in ('PAID','REFUND_PENDING')""").param("id", id).param("at", Timestamp.from(at)).update() > 0;
    }

    /** A paid, refund-pending or refunded order to CHARGEBACK; false when it is in any other status. */
    boolean markChargeback(UUID id) {
        return jdbc.sql("""
                        update billing_orders set status = 'CHARGEBACK', updated_at = now()
                        where id = :id and status in ('PAID','REFUND_PENDING','REFUNDED')""").param("id", id).update() > 0;
    }

    /** PAID to REFUND_PENDING with the reason and the moment; false when the order is not PAID. */
    boolean markRefundPending(UUID id, RefundReason reason, Instant at) {
        return jdbc.sql("""
                                update billing_orders
                                set status = 'REFUND_PENDING', refund_reason = :reason, refund_requested_at = :at,
                                    updated_at = now()
                                where id = :id and status = 'PAID'""")
                        .param("id", id)
                        .param("reason", reason.name())
                        .param("at", Timestamp.from(at))
                        .update()
                > 0;
    }

    /** The family's newest order still in motion: waiting for payment or waiting for a refund. */
    Optional<OrderRow> latestOpen(UUID ws) {
        return jdbc.sql("select " + COLUMNS + """
                         from billing_orders
                        where workspace_id = :ws and status in ('CREATED', 'REFUND_PENDING')
                        order by created_at desc limit 1""")
                .param("ws", ws)
                .query(OrderRepository::map)
                .optional();
    }

    /** Orders waiting for their refund that were asked for after the cutoff, oldest first. */
    List<OrderRow> refundPendingSince(Instant cutoff) {
        return jdbc.sql("select " + COLUMNS + """
                         from billing_orders
                        where status = 'REFUND_PENDING' and refund_requested_at > :cutoff
                        order by refund_requested_at, id""")
                .param("cutoff", Timestamp.from(cutoff))
                .query(OrderRepository::map)
                .list();
    }

    /**
     * Orders not paid on our side (waiting, canceled or expired) whose checkout was opened after the cutoff, oldest
     * first: a payment the webhook never told us about is still found.
     */
    List<OrderRow> unconfirmedCheckoutsSince(Instant cutoff) {
        return jdbc.sql("select " + COLUMNS + """
                         from billing_orders
                        where status in ('CREATED', 'CANCELED', 'EXPIRED') and asaas_checkout_id is not null
                          and created_at > :cutoff
                        order by created_at, id""")
                .param("cutoff", Timestamp.from(cutoff))
                .query(OrderRepository::map)
                .list();
    }

    /** Paid orders that have a checkout but not yet the payment id, oldest first. */
    List<OrderRow> paidWithoutPaymentId() {
        return jdbc.sql("select " + COLUMNS + """
                         from billing_orders
                        where status = 'PAID' and asaas_payment_id is null and asaas_checkout_id is not null
                        order by paid_at, id""").query(OrderRepository::map).list();
    }

    private static OrderRow map(ResultSet rs, int row) throws SQLException {
        String reason = rs.getString("refund_reason");
        return new OrderRow(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("requested_by", UUID.class),
                rs.getString("product"),
                rs.getLong("amount_cents"),
                rs.getString("currency"),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getString("asaas_checkout_id"),
                rs.getString("asaas_payment_id"),
                instant(rs, "paid_at"),
                instant(rs, "refund_requested_at"),
                instant(rs, "refunded_at"),
                reason == null ? null : RefundReason.valueOf(reason),
                instant(rs, "created_at"));
    }

    private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
