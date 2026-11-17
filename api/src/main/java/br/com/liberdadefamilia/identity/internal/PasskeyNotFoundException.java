package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The passkey does not exist, or belongs to someone else: the two answer the same. */
final class PasskeyNotFoundException extends DomainException {

    PasskeyNotFoundException() {
        super(404, "passkey.not-found", "Passkey não encontrada.");
    }
}
