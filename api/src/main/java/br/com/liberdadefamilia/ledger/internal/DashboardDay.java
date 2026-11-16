package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.Money;
import java.time.LocalDate;

/** The family's balance at the end of a day. */
record DashboardDay(LocalDate day, Money balance) {}
