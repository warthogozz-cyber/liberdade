package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.shared.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The budgets of the billing routes. They count with the application clock. */
@Configuration(proxyBeanMethods = false)
class BillingRateLimits {

    /** Checkouts started by one person: 5 in ten minutes. */
    @Bean
    RateLimiter checkoutRateLimiter(Clock clock) {
        return new RateLimiter(clock, 5, Duration.ofMinutes(10));
    }
}
