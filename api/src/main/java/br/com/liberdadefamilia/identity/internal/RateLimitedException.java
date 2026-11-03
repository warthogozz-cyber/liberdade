package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** Too many sign-in attempts from one address in the current minute. */
final class RateLimitedException extends DomainException {

    RateLimitedException() {
        super(429, "rate.limited", "Muitas tentativas. Espere um minuto e tente de novo.");
    }
}
