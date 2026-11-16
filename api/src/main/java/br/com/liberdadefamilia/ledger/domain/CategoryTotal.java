package br.com.liberdadefamilia.ledger.domain;

import br.com.liberdadefamilia.shared.Money;
import java.util.UUID;

/** What a top-level category (its subcategories folded in) spent this month and last month. */
public record CategoryTotal(UUID categoryId, Money current, Money previous) {}
