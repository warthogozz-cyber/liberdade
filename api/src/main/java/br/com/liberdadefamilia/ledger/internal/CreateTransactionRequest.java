package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.Money;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A new transaction as the device sends it. {@code categoryId} is required while transfers do not exist (D23). The
 * description is stripped, and a blank one becomes null, before validation.
 */
record CreateTransactionRequest(
        @NotNull UUID id,
        @NotNull TransactionKind kind,
        @NotNull @TransactionAmount Money amount,
        @NotNull LocalDate occurredOn,
        @NotNull UUID accountId,
        @NotNull UUID categoryId,

        @Nullable @Size(max = CreateTransactionRequest.MAX_DESCRIPTION_LENGTH)
        String description) {

    static final int MAX_DESCRIPTION_LENGTH = 140;

    CreateTransactionRequest {
        if (description != null) {
            description = description.strip();
            if (description.isEmpty()) {
                description = null;
            }
        }
    }
}
