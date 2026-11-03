package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** A well-formed transaction that the family's data refuses. The message is the code only, never a value. */
final class TransactionRejectedException extends DomainException {

    TransactionRejectedException(String code, String detail) {
        super(422, code, detail);
    }
}
