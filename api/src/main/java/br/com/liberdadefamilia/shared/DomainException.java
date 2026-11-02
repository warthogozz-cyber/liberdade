package br.com.liberdadefamilia.shared;

/** Base class for business-rule violations that the API maps to RFC 9457 problem responses. */
public abstract class DomainException extends RuntimeException {

    private final int status;
    private final String code;
    private final String detail;

    protected DomainException(int status, String code, String detail) {
        super(code);
        this.status = status;
        this.code = code;
        this.detail = detail;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String detail() {
        return detail;
    }
}
