package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The thing is not in this family. The message is the code only, never a value. */
final class LedgerNotFoundException extends DomainException {

    LedgerNotFoundException(String code, String detail) {
        super(404, code, detail);
    }
}
