package br.com.liberdadefamilia.ledger;

import br.com.liberdadefamilia.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A transaction as the data export carries it. {@code kind} is the enum name ({@code EXPENSE}, {@code INCOME}, ...). */
public record ExportedTransaction(
        UUID id,
        String kind,
        Money amount,
        LocalDate occurredOn,
        String accountName,
        @Nullable String toAccountName,
        @Nullable String categoryName,
        @Nullable String description,
        UUID memberId,
        Instant createdAt) {}
