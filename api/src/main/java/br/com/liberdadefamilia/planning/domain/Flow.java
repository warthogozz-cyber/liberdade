package br.com.liberdadefamilia.planning.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A dated cash movement: positive for income, negative for expense, zero for a transfer. */
public record Flow(LocalDate on, BigDecimal signedAmount) {}
