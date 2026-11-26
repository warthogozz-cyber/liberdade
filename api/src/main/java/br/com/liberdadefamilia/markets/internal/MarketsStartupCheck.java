package br.com.liberdadefamilia.markets.internal;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Stops a fake source from reaching production, where it would show invented markets and places, and a state in
 * {@code nfce.fetch-states} that has no reader.
 */
@Component
public class MarketsStartupCheck implements ApplicationRunner {

    private final MarketsProperties properties;
    private final Environment environment;
    private final List<NfceStateReader> readers;

    @Autowired
    MarketsStartupCheck(
            MarketsProperties properties, Environment environment, ObjectProvider<NfceStateReader> readers) {
        this(properties, environment, readers.orderedStream().toList());
    }

    MarketsStartupCheck(MarketsProperties properties, Environment environment, List<NfceStateReader> readers) {
        this.properties = properties;
        this.environment = environment;
        this.readers = readers;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.anyFake() && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("markets source=fake is not allowed in prod");
        }
        for (String state : properties.nfce().fetchStates()) {
            if (readers.stream().noneMatch(reader -> state.equalsIgnoreCase(reader.uf()))) {
                throw new IllegalStateException("markets nfce.fetch-states lists " + state + ", which has no reader");
            }
        }
    }
}
