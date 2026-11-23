package br.com.liberdadefamilia.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Money that is still to come in or go out on a day: a planned transaction, or a confirmed one dated ahead. */
public record ScheduledFlow(LocalDate on, TransactionKind kind, BigDecimal amount) {}
