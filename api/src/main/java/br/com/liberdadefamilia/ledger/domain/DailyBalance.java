package br.com.liberdadefamilia.ledger.domain;

import br.com.liberdadefamilia.shared.Money;
import java.time.LocalDate;

/** The family's balance at the end of a day. */
public record DailyBalance(LocalDate day, Money balance) {}
