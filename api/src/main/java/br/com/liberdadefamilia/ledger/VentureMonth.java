package br.com.liberdadefamilia.ledger;

import java.math.BigDecimal;
import java.time.YearMonth;

/** The confirmed income and expense marked with one venture in one month. Exact sums, never rounded. */
public record VentureMonth(YearMonth month, BigDecimal income, BigDecimal expense) {}
