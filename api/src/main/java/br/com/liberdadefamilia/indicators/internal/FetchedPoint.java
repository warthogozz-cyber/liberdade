package br.com.liberdadefamilia.indicators.internal;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One value the source returned for one day, with the exact public URL it came from. */
public record FetchedPoint(LocalDate refDate, BigDecimal value, String sourceUrl) {}
