/**
 * What the family plans ahead: recurring rules and the planned transactions they generate. A planned transaction
 * stays out of every total until it is confirmed.
 */
@ApplicationModule(allowedDependencies = {"workspace", "ledger", "shared"})
package br.com.liberdadefamilia.planning;

import org.springframework.modulith.ApplicationModule;
