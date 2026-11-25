package br.com.liberdadefamilia.ventures;

import br.com.liberdadefamilia.shared.Money;
import java.util.UUID;

/** A cost item of a project as the data export carries it. {@code kind} is {@code INITIAL} or {@code FIXED_MONTHLY}. */
public record ExportedVentureItem(UUID id, String kind, String name, Money amount, int position) {}
