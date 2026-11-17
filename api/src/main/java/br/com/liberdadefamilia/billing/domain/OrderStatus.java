package br.com.liberdadefamilia.billing.domain;

/** Where an order is in its life. */
public enum OrderStatus {
    CREATED,
    PAID,
    EXPIRED,
    CANCELED,
    REFUND_PENDING,
    REFUNDED,
    CHARGEBACK;

    /** A payment that arrives late still counts for an order we had given up on. */
    public boolean canBecomePaid() {
        return this == CREATED || this == EXPIRED || this == CANCELED;
    }
}
