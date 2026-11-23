package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.PlanView;
import br.com.liberdadefamilia.billing.Plans;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** The plan is worked out when read: an ended plan is FREE, with no job to expire it. */
@Service
class PlanService implements Plans {

    private static final PlanView FREE = new PlanView(PlanKey.FREE, null, false);

    private final PlanRepository plans;
    private final BillingProperties properties;
    private final Clock clock;

    PlanService(PlanRepository plans, BillingProperties properties, Clock clock) {
        this.plans = plans;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public PlanView current(UUID workspaceId) {
        Instant now = clock.instant();
        return plans.find(workspaceId)
                .filter(row -> row.endsAt().isAfter(now))
                .map(row -> new PlanView(PlanKey.FAMILY, row.endsAt(), row.founder()))
                .orElse(FREE);
    }

    @Override
    public boolean legalSetEnabled() {
        return properties.enabled();
    }
}
