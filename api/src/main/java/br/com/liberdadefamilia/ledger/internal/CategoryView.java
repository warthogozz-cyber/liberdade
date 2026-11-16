package br.com.liberdadefamilia.ledger.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A category as {@code GET /categories} lists it. */
record CategoryView(
        UUID id,
        String name,
        TransactionKind kind,
        @Nullable @Schema(nullable = true) String icon) {}
