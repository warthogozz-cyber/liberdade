package br.com.liberdadefamilia.indicators.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** How an indicator moved. The value is already rounded to two decimals; {@code since} is the date it counts from. */
public record Variation(BigDecimal value, VariationUnit unit, VariationBasis basis, LocalDate since) {}
