package br.com.liberdadefamilia.ledger.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A tag as the API lists it. The list holds active tags, so {@code archivedAt} is null there. */
record TagView(
        UUID id,
        String name,
        @Nullable @Schema(nullable = true) Instant archivedAt) {}
