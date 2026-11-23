package br.com.liberdadefamilia.shared;

/** How a transaction was paid. Which of these a family is offered depends on its country ({@link PaymentMethods}). */
public enum PaymentMethod {
    PIX,
    CREDIT_CARD,
    DEBIT_CARD,
    BOLETO,
    BANK_TRANSFER,
    CASH
}
