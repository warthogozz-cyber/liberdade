package br.com.liberdadefamilia.billing.internal;

/** The gateway refused or could not be reached. The message never carries the response body. */
public class PaymentGatewayException extends RuntimeException {

    private final int status;

    /** @param status the HTTP status, or 0 when there was no response (timeout, IO error) */
    public PaymentGatewayException(int status) {
        super("asaas status " + status);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
