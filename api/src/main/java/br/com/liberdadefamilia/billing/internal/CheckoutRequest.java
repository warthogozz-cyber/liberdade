package br.com.liberdadefamilia.billing.internal;

import java.util.UUID;

/**
 * What the gateway needs to open a checkout.
 *
 * @param callbackUrl where the payer returns to: {@code {web.origin}/mais/plano?pedido={orderId}}
 */
public record CheckoutRequest(UUID orderId, String callbackUrl) {}
