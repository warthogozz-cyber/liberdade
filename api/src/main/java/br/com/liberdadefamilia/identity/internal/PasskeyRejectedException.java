package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.shared.DomainException;

final class PasskeyRejectedException extends DomainException {

    PasskeyRejectedException() {
        super(400, "passkey.rejected", "Não foi possível criar a passkey. Tente de novo.");
    }
}
