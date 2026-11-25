package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.shared.Money;
import java.util.UUID;

/** A cost line of a project. {@code isExample} is true while it still holds the model's value. */
record CostItemView(UUID id, String name, Money amount, boolean isExample) {}
