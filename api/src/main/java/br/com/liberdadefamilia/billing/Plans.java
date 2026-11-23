package br.com.liberdadefamilia.billing;

import java.util.UUID;

/** What other modules may ask of billing. */
public interface Plans {

    /** The plan the family is on at this moment: FREE when it has none or the last one has ended. */
    PlanView current(UUID workspaceId);

    /** Whether selling is on, which is also whether the paid-plan legal documents apply. */
    boolean legalSetEnabled();
}
