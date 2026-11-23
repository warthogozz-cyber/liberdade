package br.com.liberdadefamilia.ledger;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.PaymentMethod;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What a planned transaction holds, minus the date and the person: the mold a recurring rule keeps. It follows the
 * rules of a new transaction: a transfer has a destination and no category, anything else a category and no
 * destination.
 */
public record PlannedTemplate(
        TransactionKind kind,
        Money amount,
        UUID accountId,
        @Nullable UUID toAccountId,
        @Nullable UUID categoryId,
        @Nullable PaymentMethod paymentMethod,
        @Nullable String description) {}
