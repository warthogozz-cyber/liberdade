/**
 * The paid plan of a family: what it bought, until when it lasts and what the family may still take back. The price
 * and the offer live in code; the payment gateway is a detail of this module.
 */
@ApplicationModule(allowedDependencies = {"identity", "workspace", "shared"})
package br.com.liberdadefamilia.billing;

import org.springframework.modulith.ApplicationModule;
