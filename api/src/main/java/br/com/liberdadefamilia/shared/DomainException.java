package br.com.liberdadefamilia.shared;

import java.util.Map;

/**
 * Base class for business-rule violations that the API maps to RFC 9457 problem responses. The properties become extra
 * members of the problem, next to {@code code} and {@code detail}.
 */
public abstract class DomainException extends RuntimeException {

    private final int status;
    private final String code;
    private final String detail;
    private final Map<String, Object> properties;

    protected DomainException(int status, String code, String detail) {
        this(status, code, detail, Map.of());
    }

    protected DomainException(int status, String code, String detail, Map<String, Object> properties) {
        super(code);
        this.status = status;
        this.code = code;
        this.detail = detail;
        this.properties = Map.copyOf(properties);
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

    /** Extra members of the problem; empty unless the exception was made with some. */
    public Map<String, Object> properties() {
        return properties;
    }
}
