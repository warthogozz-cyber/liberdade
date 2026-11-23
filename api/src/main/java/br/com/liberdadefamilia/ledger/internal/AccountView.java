package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An account as the API shows it; {@code balance} is computed from the transactions. {@code isReserve} says whether it
 * is part of the emergency reserve.
 */
record AccountView(
        UUID id,
        String name,
        AccountType type,
        Currency currency,
        Money initialBalance,
        Money balance,

        @Nullable @Schema(nullable = true) UUID ownerMembershipId,

        @Nullable @Schema(nullable = true) Instant archivedAt,
        boolean isReserve) {}
