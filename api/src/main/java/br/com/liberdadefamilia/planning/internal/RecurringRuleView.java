package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.ledger.TransactionKind;
import br.com.liberdadefamilia.planning.domain.Frequency;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A recurring rule as the API returns it. {@code nextDueOn} is the first date after the generated horizon, or null
 * when the rule has no date left. {@code version} goes back in an edit, for the optimistic lock.
 */
record RecurringRuleView(
        UUID id,
        TransactionKind kind,
        Money amount,
        UUID accountId,
        @Nullable @Schema(nullable = true) UUID toAccountId,
        @Nullable @Schema(nullable = true) UUID categoryId,
        @Nullable @Schema(nullable = true) PaymentMethod paymentMethod,
        @Nullable @Schema(nullable = true) String description,
        Frequency frequency,
        int interval,
        LocalDate startOn,
        @Nullable @Schema(nullable = true) LocalDate endOn,
        @Nullable @Schema(nullable = true) LocalDate nextDueOn,
        @Nullable @Schema(nullable = true) UUID createdBy,
        long version) {}
