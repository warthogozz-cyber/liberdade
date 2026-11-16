package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The request clashes with what the family already has. The message is the code only, never a value. */
final class LedgerConflictException extends DomainException {

    LedgerConflictException(String code, String detail) {
        super(409, code, detail);
    }
}
