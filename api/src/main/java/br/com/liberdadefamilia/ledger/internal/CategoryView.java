package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.domain.TransactionKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A category as the API lists it. A subcategory carries the id of its parent; an archived one, when it was archived. */
record CategoryView(
        UUID id,
        String name,
        TransactionKind kind,
        @Nullable @Schema(nullable = true) String icon,
        @Nullable @Schema(nullable = true) CategoryColor color,
        @Nullable @Schema(nullable = true) UUID parentId,
        @Nullable @Schema(nullable = true) Instant archivedAt) {}
