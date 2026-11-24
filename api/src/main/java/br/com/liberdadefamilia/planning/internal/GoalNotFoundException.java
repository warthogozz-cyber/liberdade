package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The family has no such goal, or it is archived; another family's is the same. */
final class GoalNotFoundException extends DomainException {

    GoalNotFoundException() {
        super(404, "goal.not-found", "Meta não encontrada.");
    }
}
