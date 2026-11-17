package br.com.liberdadefamilia.billing.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Stops the fake gateway from reaching production and warns when selling is on but not set up. */
@Component
class BillingStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BillingStartupCheck.class);

    private final BillingProperties properties;
    private final BillingStatus status;
    private final Environment environment;

    BillingStartupCheck(BillingProperties properties, BillingStatus status, Environment environment) {
        this.properties = properties;
        this.status = status;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.fakeGateway() && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("billing.gateway=fake is not allowed in prod");
        }
        if (properties.enabled() && !status.configured()) {
            // No values: the key and the token are secrets.
            log.warn("billing enabled but not configured");
        }
    }
}
