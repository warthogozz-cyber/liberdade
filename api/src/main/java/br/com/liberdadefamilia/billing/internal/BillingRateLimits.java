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

    /** The payer's return page asking about an order: 30 a minute for the family. */
    @Bean
    RateLimiter refreshRateLimiter(Clock clock) {
        return new RateLimiter(clock, 30, Duration.ofMinutes(1));
    }

    /** Refunds the owner asks for: 5 in ten minutes for the family. */
    @Bean
    RateLimiter refundRateLimiter(Clock clock) {
        return new RateLimiter(clock, 5, Duration.ofMinutes(10));
    }

    /** The gateway's authenticated calls to the webhook: 120 a minute, all under one key. */
    @Bean
    RateLimiter webhookRateLimiter(Clock clock) {
        return new RateLimiter(clock, 120, Duration.ofMinutes(1));
    }

    /**
     * Webhook calls without a valid token: 20 a minute for each client address. A flood costs little and never
     * touches the gateway's own budget.
     */
    @Bean
    RateLimiter webhookUnauthenticatedRateLimiter(Clock clock) {
        return new RateLimiter(clock, 20, Duration.ofMinutes(1));
    }
}
