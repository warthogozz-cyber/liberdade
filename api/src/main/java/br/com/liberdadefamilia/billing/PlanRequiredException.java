package br.com.liberdadefamilia.billing;

import br.com.liberdadefamilia.shared.DomainException;

/** The family's plan is below what the route needs. */
public class PlanRequiredException extends DomainException {

    public PlanRequiredException() {
        super(403, "plan.required", "Este recurso é do plano Família.");
    }
}
