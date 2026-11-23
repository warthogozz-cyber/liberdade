package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The rule changed since the caller read it. */
final class RecurringRuleConflictException extends DomainException {

    RecurringRuleConflictException() {
        super(
                409,
                "recurring-rule.version-conflict",
                "A regra foi alterada por outra pessoa. Recarregue e tente de novo.");
    }
}
