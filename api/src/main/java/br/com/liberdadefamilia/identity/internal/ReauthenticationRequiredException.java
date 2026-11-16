package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** A sensitive action came without a sign-in from the last 10 minutes. */
final class ReauthenticationRequiredException extends DomainException {

    ReauthenticationRequiredException() {
        super(403, "auth.reauthentication-required", "Confirme que é você para continuar.");
    }
}
