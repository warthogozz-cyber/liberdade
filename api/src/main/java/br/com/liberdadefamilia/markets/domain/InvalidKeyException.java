package br.com.liberdadefamilia.markets.domain;

/** An NFC-e access key that fails a rule. Carries only the fixed reason, never the key. */
public class InvalidKeyException extends RuntimeException {

    public enum Reason {
        LENGTH,
        UF,
        MONTH,
        CNPJ,
        MODEL,
        CHECK_DIGIT
    }

    private final Reason reason;

    public InvalidKeyException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
