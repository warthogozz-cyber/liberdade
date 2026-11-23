package br.com.liberdadefamilia.billing.internal;

import java.net.URI;
import java.util.UUID;

/** The order that was opened and the page the payer pays on. */
record CheckoutView(UUID orderId, URI checkoutUrl) {}
