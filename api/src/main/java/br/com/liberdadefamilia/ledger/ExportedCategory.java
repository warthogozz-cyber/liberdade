package br.com.liberdadefamilia.ledger;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A category as the data export carries it. {@code kind} is {@code INCOME} or {@code EXPENSE}; {@code isFixed} marks a
 * fixed cost.
 */
public record ExportedCategory(
        UUID id,
        @Nullable UUID parentId,
        String kind,
        String name,
        @Nullable Instant archivedAt,
        boolean isFixed) {}
