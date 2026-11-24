package br.com.liberdadefamilia.planning;

import java.time.YearMonth;
import java.util.UUID;

/** A limit of the month was set, changed, removed or copied in. Published inside the transaction that wrote it. */
public record BudgetChanged(UUID workspaceId, YearMonth month) {}
