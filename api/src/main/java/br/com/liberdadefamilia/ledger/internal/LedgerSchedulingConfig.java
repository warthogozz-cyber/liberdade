package br.com.liberdadefamilia.ledger.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on the scheduler for the ledger's nightly jobs, such as {@link TransactionPurgeJob}. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class LedgerSchedulingConfig {}
