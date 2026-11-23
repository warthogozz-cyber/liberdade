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
 * a category and no destination. {@code tags} are the names, by name, archived tags included. {@code version} goes
 * back in an edit, for the optimistic lock.
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
        UUID memberId,
        Instant createdAt,
        long version) {

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
                memberId,
                createdAt,
                version);
    }
}
