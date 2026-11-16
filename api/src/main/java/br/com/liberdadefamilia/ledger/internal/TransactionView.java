package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A transaction as the API returns it. */
record TransactionView(
        UUID id,
        TransactionKind kind,
        Money amount,
        LocalDate occurredOn,
        UUID accountId,
        @Nullable UUID categoryId,
        @Nullable @Schema(nullable = true) String description,
        UUID memberId,
        Instant createdAt) {}
