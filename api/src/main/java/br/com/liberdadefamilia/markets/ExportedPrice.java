package br.com.liberdadefamilia.markets;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One observed price as the data export carries it. {@code receiptId} is empty for a price without a receipt. */
public record ExportedPrice(
        UUID productId,
        UUID marketId,
        @Nullable UUID receiptId,
        LocalDate observedOn,
        String unit,
        @Nullable BigDecimal quantity,
        BigDecimal unitPrice) {}
