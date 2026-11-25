package br.com.liberdadefamilia.markets.internal;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Stops a fake source from reaching production, where it would show invented markets and places. */
@Component
public class MarketsStartupCheck implements ApplicationRunner {

    private final MarketsProperties properties;
    private final Environment environment;

    public MarketsStartupCheck(MarketsProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.anyFake() && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("markets source=fake is not allowed in prod");
        }
    }
}
