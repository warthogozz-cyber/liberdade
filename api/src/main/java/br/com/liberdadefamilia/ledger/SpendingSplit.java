package br.com.liberdadefamilia.ledger;

import java.math.BigDecimal;

/**
 * Confirmed spending split into fixed costs (from a recurring rule, or in a fixed category or the child of one) and
 * everything else.
 */
public record SpendingSplit(BigDecimal fixed, BigDecimal variable) {}
