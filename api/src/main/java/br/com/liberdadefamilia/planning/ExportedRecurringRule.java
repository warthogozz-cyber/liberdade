package br.com.liberdadefamilia.planning;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.PaymentMethod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A recurring rule as the data export carries it: what the rule view has, plus when it was made and archived.
 * {@code kind} and {@code frequency} are the enum names ({@code EXPENSE}, {@code MONTHLY}, ...).
 */
public record ExportedRecurringRule(
        UUID id,
        String kind,
        Money amount,
        UUID accountId,
        @Nullable UUID toAccountId,
        @Nullable UUID categoryId,
        @Nullable PaymentMethod paymentMethod,
        @Nullable String description,
        String frequency,
        int interval,
        LocalDate startOn,
        @Nullable LocalDate endOn,
        @Nullable LocalDate nextDueOn,
        @Nullable UUID createdBy,
        long version,
        Instant createdAt,
        @Nullable Instant archivedAt) {}
