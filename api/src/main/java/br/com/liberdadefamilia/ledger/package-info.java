/**
 * The family's money: its accounts, its categories and the transactions between them. A new family gets a wallet and
 * the default categories (D29); balances and the month's totals are computed from the transactions, never stored.
 */
@ApplicationModule(allowedDependencies = {"workspace", "shared"})
package br.com.liberdadefamilia.ledger;

import org.springframework.modulith.ApplicationModule;
