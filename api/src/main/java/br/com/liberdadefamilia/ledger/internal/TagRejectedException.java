package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** A well-formed tag request that the family's tags refuse. The message is the code only, never a name. */
final class TagRejectedException extends DomainException {

    TagRejectedException(String code, String detail) {
        super(422, code, detail);
    }
}
