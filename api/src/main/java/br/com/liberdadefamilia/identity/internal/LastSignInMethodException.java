package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** Removing the passkey would leave the person with no way to sign in. */
final class LastSignInMethodException extends DomainException {

    LastSignInMethodException() {
        super(
                422,
                "passkey.last-method",
                "Esta é a sua única forma de entrar. Cadastre outra passkey antes de remover esta.");
    }
}
