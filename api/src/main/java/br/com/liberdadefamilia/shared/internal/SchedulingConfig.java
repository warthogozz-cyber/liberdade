package br.com.liberdadefamilia.shared.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on the scheduler for the nightly purge jobs of every module. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {}
