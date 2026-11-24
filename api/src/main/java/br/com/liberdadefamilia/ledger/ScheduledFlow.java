package br.com.liberdadefamilia.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Money that is still to come in or go out of the counted accounts on a day: a planned transaction, or a confirmed one
 * dated ahead. {@code kind} is {@code INCOME} for money coming in and {@code EXPENSE} for money going out, also when
 * the transaction is a transfer with one archived end; {@code amount} is positive.
 */
public record ScheduledFlow(LocalDate on, TransactionKind kind, BigDecimal amount) {}
