package br.com.liberdadefamilia.billing;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** One order as the family may take it with them: no gateway ids and no author. */
public record ExportedOrder(
        String product,
        long amountCents,
        String currency,
        String status,
        Instant createdAt,
        @Nullable Instant paidAt,
        @Nullable Instant refundedAt) {}
