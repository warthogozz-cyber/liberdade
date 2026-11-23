package br.com.liberdadefamilia.shared.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the scheduler for the jobs of every module. {@code liberdade.scheduling.enabled=false} turns it off, which
 * the tests do so a job never races the test that calls it directly.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "liberdade.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {}
