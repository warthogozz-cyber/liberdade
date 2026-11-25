package br.com.liberdadefamilia.ventures.internal;

import java.math.BigDecimal;

/** A product of a template: unit cost, unit price and the planned sales per month. */
public record TemplateProduct(String name, BigDecimal unitVariableCost, BigDecimal unitPrice, int plannedUnitsMonth) {}
