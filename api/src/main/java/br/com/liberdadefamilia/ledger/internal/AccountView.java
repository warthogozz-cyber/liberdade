package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.Money;
import java.util.Currency;
import java.util.UUID;

/** An account as {@code GET /accounts} lists it. */
record AccountView(UUID id, String name, AccountType type, Currency currency, Money balance) {}
