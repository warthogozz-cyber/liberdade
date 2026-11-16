package br.com.liberdadefamilia.ledger.domain;

import br.com.liberdadefamilia.shared.Money;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A line of the ranking; the one without a category is "Outras", the sum of what did not make the cut. */
public record RankedCategory(@Nullable UUID categoryId, Money current, Money previous) {}
