/**
 * The weekly streak and the achievements a member earns by using the product. What a member earns depends on the
 * role they have and on the modules their family turned on.
 */
@ApplicationModule(allowedDependencies = {"workspace", "ledger", "planning", "shared"})
package br.com.liberdadefamilia.gamification;

import org.springframework.modulith.ApplicationModule;
