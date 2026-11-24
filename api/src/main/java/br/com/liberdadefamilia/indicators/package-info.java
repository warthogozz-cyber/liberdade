/**
 * The public market indicators of the Banco Central (Selic, CDI, IPCA, dollar) and the real-return calculator. The
 * data is the same for every family, so nothing here is scoped to a workspace.
 */
@ApplicationModule(allowedDependencies = {"workspace", "shared"})
package br.com.liberdadefamilia.indicators;

import org.springframework.modulith.ApplicationModule;
