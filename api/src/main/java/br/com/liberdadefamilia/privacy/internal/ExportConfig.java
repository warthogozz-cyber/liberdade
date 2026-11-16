package br.com.liberdadefamilia.privacy.internal;

import br.com.liberdadefamilia.shared.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The budget of the data exports. */
@Configuration(proxyBeanMethods = false)
class ExportConfig {

    /** Both exports together: 10 an hour per person. */
    @Bean
    RateLimiter exportRateLimiter(Clock clock) {
        return new RateLimiter(clock, 10, Duration.ofHours(1));
    }
}
