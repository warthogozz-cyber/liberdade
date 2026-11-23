package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.TransactionAmount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Confirms a planned transaction, as it is or with what really happened. {@code version} is the one the screen was
 * opened with. A null {@code amount}, {@code occurredOn} or {@code accountId} keeps what the planned transaction has;
 * the ones sent follow the rules of a new transaction.
 */
record ConfirmTransactionRequest(
        @NotNull Long version,

        @Nullable @Schema(nullable = true) @TransactionAmount
        Money amount,

        @Nullable @Schema(nullable = true) LocalDate occurredOn,
        @Nullable @Schema(nullable = true) UUID accountId) {}
