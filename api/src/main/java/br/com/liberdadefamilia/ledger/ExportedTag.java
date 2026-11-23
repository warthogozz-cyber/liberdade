package br.com.liberdadefamilia.ledger;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A tag as the data export carries it. {@code createdBy} is a membership id, null once that membership is gone. */
public record ExportedTag(
        UUID id,
        String name,
        @Nullable UUID createdBy,
        Instant createdAt,
        @Nullable Instant archivedAt) {}
