package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.domain.FounderTheme;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The family's plan as the plan page needs it, with what the caller may do about it.
 *
 * @param refundDeadline when the refund closes, exclusive: 00:00 in São Paulo after the last allowed day, so the page
 *     shows the day before it
 */
record PlanDetails(
        PlanKey key,
        boolean founder,
        @Nullable @Schema(nullable = true) Instant endsAt,
        FounderTheme theme,
        boolean presaleOpen,
        boolean canPurchase,
        boolean canRefund,
        @Nullable @Schema(nullable = true) Instant refundDeadline,
        @Nullable @Schema(nullable = true) UUID paidOrderId,
        @Nullable @Schema(nullable = true) OrderView openOrder) {}
