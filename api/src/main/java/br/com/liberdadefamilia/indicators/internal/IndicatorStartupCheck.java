package br.com.liberdadefamilia.indicators.internal;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Stops the fake source from reaching production, where it would show invented rates. */
@Component
public class IndicatorStartupCheck implements ApplicationRunner {

    private final IndicatorProperties properties;
    private final Environment environment;

    public IndicatorStartupCheck(IndicatorProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.fake() && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("indicators.source=fake is not allowed in prod");
        }
    }
}
