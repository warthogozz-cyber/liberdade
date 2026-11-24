package br.com.liberdadefamilia.ledger;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.PaymentMethod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A transaction as the data export carries it. {@code kind} is the enum name ({@code EXPENSE}, {@code INCOME}, ...) and
 * {@code status} is {@code CONFIRMED} or {@code PLANNED}. {@code tags} are the names of the tags on it, in name order. {@code importKey} is the hash that marks a transaction
 * brought in by a statement import, null for the others.
 */
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
        Instant createdAt,
        String status,
        @Nullable PaymentMethod paymentMethod,
        List<String> tags,
        @Nullable LocalDate scheduledOn,
        @Nullable UUID recurringRuleId,
        @Nullable String importKey) {}
