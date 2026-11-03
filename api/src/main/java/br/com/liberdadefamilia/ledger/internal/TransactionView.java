package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.Money;
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
        @Nullable String description,
        UUID memberId,
        Instant createdAt) {}
