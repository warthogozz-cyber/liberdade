package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** A well-formed rule request that the rules of recurrence refuse. */
final class RecurringRuleRejectedException extends DomainException {

    RecurringRuleRejectedException(String code, String detail) {
        super(422, code, detail);
    }
}
