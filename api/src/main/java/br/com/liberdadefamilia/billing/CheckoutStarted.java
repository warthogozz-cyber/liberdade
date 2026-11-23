package br.com.liberdadefamilia.billing;

import java.util.UUID;

/** The owner started the Founder checkout. Published inside the transaction that stores the order. */
public record CheckoutStarted(UUID workspaceId, UUID orderId, UUID actorUserId) {}
