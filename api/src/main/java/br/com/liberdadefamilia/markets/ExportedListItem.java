package br.com.liberdadefamilia.markets;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One item of the family's shopping list as the data export carries it; {@code productId} is in the products export. */
public record ExportedListItem(
        UUID productId, BigDecimal quantity, @Nullable Instant checkedAt, int position, Instant createdAt) {}
