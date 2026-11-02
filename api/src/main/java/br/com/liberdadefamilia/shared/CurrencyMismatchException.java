package br.com.liberdadefamilia.shared;

/** Thrown when an operation mixes two different currencies. */
public final class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException() {
        super(422, "money.currency-mismatch", "As moedas dos valores não são iguais.");
    }
}
