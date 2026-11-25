package br.com.liberdadefamilia.ventures.domain;

import java.math.BigDecimal;

/** A product the project sells: unit cost, unit price and the planned sales per month. */
public record ProductLine(BigDecimal unitVariableCost, BigDecimal unitPrice, int plannedUnitsMonth) {}
