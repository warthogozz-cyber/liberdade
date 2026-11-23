package br.com.liberdadefamilia.billing.internal;

/**
 * A payment as the gateway reports it. There is no payment time on purpose: Asaas gives only the day, so a confirmed
 * payment counts from the moment we see it.
 */
public record PaymentInfo(String paymentId, String status) {}
