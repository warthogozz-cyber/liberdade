package br.com.liberdadefamilia.billing.internal;

import java.net.URI;

/** A checkout the gateway opened: its id and the page the payer pays on. */
public record CheckoutSession(String checkoutId, URI link) {}
