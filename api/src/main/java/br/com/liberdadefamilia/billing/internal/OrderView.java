package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.RefundReason;
import br.com.liberdadefamilia.billing.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** An order as the payer's return page sees it. */
record OrderView(
        UUID id,
        OrderStatus status,
        @Nullable @Schema(nullable = true) RefundReason refundReason) {}
