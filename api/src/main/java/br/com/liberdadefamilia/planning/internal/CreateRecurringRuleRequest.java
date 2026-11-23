package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.ledger.TransactionKind;
import br.com.liberdadefamilia.planning.domain.Frequency;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.PaymentMethod;
import br.com.liberdadefamilia.shared.TransactionAmount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A new recurring rule. The description is stripped; blank means none. */
record CreateRecurringRuleRequest(
        @NotNull TransactionKind kind,
        @NotNull @TransactionAmount Money amount,
        @NotNull UUID accountId,
        @Nullable @Schema(nullable = true) UUID toAccountId,
        @Nullable @Schema(nullable = true) UUID categoryId,
        @Nullable @Schema(nullable = true) PaymentMethod paymentMethod,
        @Nullable @Schema(nullable = true) @Size(max = 140) String description,
        @NotNull Frequency frequency,
        @Min(1) @Max(12) int interval,
        @NotNull LocalDate startOn,
        @Nullable @Schema(nullable = true) LocalDate endOn) {

    CreateRecurringRuleRequest {
        description = RecurringRuleService.cleanDescription(description);
    }
}
