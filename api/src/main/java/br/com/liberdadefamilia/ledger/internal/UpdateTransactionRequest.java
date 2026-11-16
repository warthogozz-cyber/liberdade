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
 * The whole transaction as the person left it on the edit screen: a full replacement, not a partial change. {@code
 * version} is the one the screen was opened with; if the transaction changed since, the edit is refused. The
 * description is cleaned as on create.
 */
record UpdateTransactionRequest(
        @NotNull Long version,
        @NotNull TransactionKind kind,
        @NotNull @TransactionAmount Money amount,
        @NotNull LocalDate occurredOn,
        @NotNull UUID accountId,
        @Nullable @Schema(nullable = true) UUID toAccountId,
        @Nullable @Schema(nullable = true) UUID categoryId,

        @Nullable
        @Schema(nullable = true)
        @Size(max = CreateTransactionRequest.MAX_DESCRIPTION_LENGTH)
        @Pattern(regexp = TextPatterns.WITHOUT_NUL)
        String description) {

    UpdateTransactionRequest {
        description = CreateTransactionRequest.cleanDescription(description);
    }
}
