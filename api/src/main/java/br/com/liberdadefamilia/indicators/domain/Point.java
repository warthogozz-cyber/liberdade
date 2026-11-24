package br.com.liberdadefamilia.indicators.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The value of an indicator on one reference date. */
public record Point(LocalDate refDate, BigDecimal value) {}
