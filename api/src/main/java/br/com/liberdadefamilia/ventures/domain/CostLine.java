package br.com.liberdadefamilia.ventures.domain;

import java.math.BigDecimal;

/** One initial or monthly fixed cost line. */
public record CostLine(BigDecimal amount) {}
