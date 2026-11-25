package br.com.liberdadefamilia.ventures;

import br.com.liberdadefamilia.shared.Money;
import java.util.UUID;

/** A product of a project as the data export carries it. */
public record ExportedVentureProduct(
        UUID id, String name, Money unitVariableCost, Money unitPrice, int plannedUnitsMonth, int position) {}
