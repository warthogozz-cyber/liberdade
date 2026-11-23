package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The family has no such active recurring rule; another family's is the same. */
final class RecurringRuleNotFoundException extends DomainException {

    RecurringRuleNotFoundException() {
        super(404, "recurring-rule.not-found", "Regra de recorrência não encontrada.");
    }
}
