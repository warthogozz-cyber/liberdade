package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.shared.Money;
import java.util.UUID;

/** A product of a project. {@code isExample} is true while it still holds the model's values. */
record ProductView(
        UUID id, String name, Money unitVariableCost, Money unitPrice, int plannedUnitsMonth, boolean isExample) {}
