package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The family is on the Grátis plan, selling is on, and it already has the active goals the plan allows (D11). */
final class GoalLimitReachedException extends DomainException {

    GoalLimitReachedException() {
        super(
                403,
                "goal.limit-reached",
                "No plano Grátis, a família tem até 3 metas ativas. Conclua ou arquive uma, ou veja o plano Família"
                        + " em Mais > Plano.");
    }
}
