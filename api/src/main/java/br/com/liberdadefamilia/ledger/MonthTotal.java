package br.com.liberdadefamilia.ledger;

import java.math.BigDecimal;
import java.time.YearMonth;

/** The confirmed spending of one month. */
public record MonthTotal(YearMonth month, BigDecimal amount) {}
