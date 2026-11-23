package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.TransactionKind;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A transaction as the API returns it. A transfer has {@code toAccountId} and no {@code categoryId}; anything else has
 * a category and no destination. {@code tags} are the names, by name, archived tags included. {@code status} is
 * {@code CONFIRMED} or {@code PLANNED}: a planned one counts in no total, and has the {@code scheduledOn} it is due,
 * and the {@code recurringRuleId} that made it, if any. {@code version} goes back in an edit, for the optimistic lock.
 */
record TransactionView(
        UUID id,
        TransactionKind kind,
        Money amount,
        LocalDate occurredOn,
        UUID accountId,
        @Nullable @Schema(nullable = true) UUID toAccountId,
        @Nullable @Schema(nullable = true) UUID categoryId,
        @Nullable @Schema(nullable = true) String description,
        @Nullable @Schema(nullable = true) PaymentMethod paymentMethod,
        List<String> tags,
        @Schema(allowableValues = {"CONFIRMED", "PLANNED"}) String status,
        @Nullable @Schema(nullable = true) LocalDate scheduledOn,
        @Nullable @Schema(nullable = true) UUID recurringRuleId,
        UUID memberId,
        Instant createdAt,
        long version) {

    static final String CONFIRMED = "CONFIRMED";
    static final String PLANNED = "PLANNED";

    boolean planned() {
        return PLANNED.equals(status);
    }

    /** The same transaction with these tag names, for the views made before its tags were saved. */
    TransactionView withTags(List<String> names) {
        return new TransactionView(
                id,
                kind,
                amount,
                occurredOn,
                accountId,
                toAccountId,
                categoryId,
                description,
                paymentMethod,
                names,
                status,
                scheduledOn,
                recurringRuleId,
                memberId,
                createdAt,
                version);
    }
}
