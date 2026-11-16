package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.domain.TransactionKind;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.TextPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A new transaction as the device sends it. A transfer has {@code toAccountId} and no {@code categoryId}; income and
 * expenses have a category and no destination, which the family rules check. The description is stripped, and a blank
 * one becomes null, before validation. {@code memberId} is who the spending was for; null means whoever records it.
 * It may hold any character but U+0000, which PostgreSQL text cannot store.
 */
record CreateTransactionRequest(
        @NotNull UUID id,
        @NotNull TransactionKind kind,
        @NotNull @TransactionAmount Money amount,
        @NotNull LocalDate occurredOn,
        @NotNull UUID accountId,
        @Nullable @Schema(nullable = true) UUID toAccountId,
        @Nullable @Schema(nullable = true) UUID categoryId,
        @Nullable @Schema(nullable = true) UUID memberId,

        @Nullable
        @Schema(nullable = true)
        @Size(max = CreateTransactionRequest.MAX_DESCRIPTION_LENGTH)
        @Pattern(regexp = TextPatterns.WITHOUT_NUL)
        String description) {

    static final int MAX_DESCRIPTION_LENGTH = 140;

    CreateTransactionRequest {
        description = cleanDescription(description);
    }

    /** The description stripped, or null when nothing is left. */
    static @Nullable String cleanDescription(@Nullable String description) {
        if (description == null) {
            return null;
        }
        String stripped = description.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
