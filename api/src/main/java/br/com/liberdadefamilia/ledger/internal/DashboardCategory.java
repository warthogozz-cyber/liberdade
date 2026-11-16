package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A line of the month's spending by category; without an id, it is "Outras": everything that did not make the cut. */
record DashboardCategory(@Nullable @Schema(nullable = true) UUID categoryId, Money current, Money previous) {}
