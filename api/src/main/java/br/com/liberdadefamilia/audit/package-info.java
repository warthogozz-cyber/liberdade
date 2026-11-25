/**
 * The audit trail of sensitive actions and the history each person may read of their own (LGPD). It listens to what
 * the other modules publish; it never keeps an address, a value or a name.
 */
@ApplicationModule(
        allowedDependencies = {"identity", "workspace", "billing", "planning", "ledger", "ventures", "shared"})
package br.com.liberdadefamilia.audit;

import org.springframework.modulith.ApplicationModule;
