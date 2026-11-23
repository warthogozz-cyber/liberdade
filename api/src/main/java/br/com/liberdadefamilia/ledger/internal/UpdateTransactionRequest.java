package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.TransactionKind;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.PaymentMethod;
import br.com.liberdadefamilia.shared.TextPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The whole transaction as the person left it on the edit screen: a full replacement, not a partial change. {@code
 * version} is the one the screen was opened with; if the transaction changed since, the edit is refused. The
 * description is cleaned as on create. A null {@code memberId} keeps the one the transaction has; a null {@code
 * paymentMethod} clears it. A null {@code tags} keeps the tags the transaction has (a client that predates tags); a list,
 * even an empty one, replaces them.
 */
record UpdateTransactionRequest(
        @NotNull Long version,
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
        String description,

        @Nullable @Schema(nullable = true) PaymentMethod paymentMethod,
        @Nullable @Schema(nullable = true) @TagNames List<String> tags) {

    UpdateTransactionRequest {
        description = CreateTransactionRequest.cleanDescription(description);
    }
}
