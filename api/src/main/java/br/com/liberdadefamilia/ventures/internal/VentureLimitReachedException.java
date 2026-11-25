package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The family is on the Grátis plan, selling is on, and it already has the open project the plan allows (D7). */
final class VentureLimitReachedException extends DomainException {

    VentureLimitReachedException() {
        super(
                403,
                "venture.limit-reached",
                "No plano Grátis, a família tem 1 projeto ativo. Encerre um, ou veja o plano Família em Mais >"
                        + " Plano.");
    }
}
