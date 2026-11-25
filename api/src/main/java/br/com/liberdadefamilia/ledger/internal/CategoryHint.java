package br.com.liberdadefamilia.ledger.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The category last used for a description, or null when the family has no history for it. */
record CategoryHint(@Nullable @Schema(nullable = true) UUID categoryId) {}
