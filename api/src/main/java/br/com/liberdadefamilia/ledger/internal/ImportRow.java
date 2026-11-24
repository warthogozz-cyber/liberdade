package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.TransactionKind;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.TextPatterns;
import br.com.liberdadefamilia.shared.TransactionAmount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One statement line the person confirmed: an income or an expense (a transfer is refused), with the id the device
 * made for it and {@code importKey}, the SHA-256 of the line in lowercase hex, which keeps the line from coming in
 * twice. The description is cleaned as on create. {@code confirmsPlannedId} is the planned transaction the line pays,
 * when the person matched one.
 */
record ImportRow(
        @NotNull UUID id,
        @NotNull TransactionKind kind,
        @NotNull @TransactionAmount Money amount,
        @NotNull LocalDate occurredOn,

        @Nullable
        @Schema(nullable = true)
        @Size(max = CreateTransactionRequest.MAX_DESCRIPTION_LENGTH)
        @Pattern(regexp = TextPatterns.WITHOUT_NUL)
        String description,

        @NotNull UUID categoryId,
        @NotNull @Pattern(regexp = "^[0-9a-f]{64}$") String importKey,
        @Nullable @Schema(nullable = true) UUID confirmsPlannedId) {

    ImportRow {
        description = CreateTransactionRequest.cleanDescription(description);
    }
}
