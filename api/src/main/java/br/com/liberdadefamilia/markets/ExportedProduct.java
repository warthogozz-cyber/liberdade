package br.com.liberdadefamilia.markets;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A product of the family as the data export carries it; {@code unit} is the enum name. */
public record ExportedProduct(
        UUID id, String name, String unit, @Nullable String gtin, Instant createdAt) {}
