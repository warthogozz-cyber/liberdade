package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** A well-formed request that the family's data refuses. The message is the code only, never a value. */
final class LedgerRejectedException extends DomainException {

    LedgerRejectedException(String code, String detail) {
        super(422, code, detail);
    }
}
