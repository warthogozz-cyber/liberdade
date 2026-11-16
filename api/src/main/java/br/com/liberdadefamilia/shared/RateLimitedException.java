package br.com.liberdadefamilia.shared;

/** Too many attempts from one key in the current window. The problem response carries {@code Retry-After}. */
public final class RateLimitedException extends DomainException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(429, "rate.limited", "Muitas tentativas. Tente de novo em instantes.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
