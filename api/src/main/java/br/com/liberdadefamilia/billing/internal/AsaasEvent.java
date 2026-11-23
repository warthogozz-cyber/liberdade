package br.com.liberdadefamilia.billing.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * The part of an Asaas webhook call that billing reads. Everything else in the body is ignored and never stored.
 *
 * @param id the event's own id, which makes a repeated delivery harmless
 * @param event the event type, such as {@code CHECKOUT_PAID}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AsaasEvent(
        String id,
        String event,
        @Nullable Checkout checkout,
        @Nullable Payment payment) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Checkout(@Nullable String id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payment(@Nullable String id, @Nullable String checkoutSession) {}
}
