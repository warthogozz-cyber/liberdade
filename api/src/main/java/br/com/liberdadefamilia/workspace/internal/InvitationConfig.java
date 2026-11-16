package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.shared.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The budgets of the invitation routes. Both count with the application clock. */
@Configuration(proxyBeanMethods = false)
class InvitationConfig {

    /** Previews and accepts together: 10 a minute per address. */
    @Bean
    RateLimiter invitationPublicRateLimiter(Clock clock) {
        return new RateLimiter(clock, 10, Duration.ofMinutes(1));
    }

    /** Links made by one person: 20 an hour. Recovery links (a later route) draw on the same one. */
    @Bean
    RateLimiter invitationCreateRateLimiter(Clock clock) {
        return new RateLimiter(clock, 20, Duration.ofHours(1));
    }
}
