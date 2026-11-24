package br.com.liberdadefamilia.ledger;

import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The confirmed spending of one category over a period, with the category's parent when it has one. */
public record CategorySpending(UUID categoryId, @Nullable UUID parentId, BigDecimal amount) {}
