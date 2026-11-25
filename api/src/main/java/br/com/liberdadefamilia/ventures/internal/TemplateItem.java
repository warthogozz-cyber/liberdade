package br.com.liberdadefamilia.ventures.internal;

import java.math.BigDecimal;

/** A cost line of a template (name and amount in reais). */
public record TemplateItem(String name, BigDecimal amount) {}
