package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.domain.TransactionKind;
import br.com.liberdadefamilia.shared.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A transaction as the API returns it. A transfer has {@code toAccountId} and no {@code categoryId}; anything else has
 * a category and no destination. {@code version} goes back in an edit, for the optimistic lock.
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
        UUID memberId,
        Instant createdAt,
        long version) {}
