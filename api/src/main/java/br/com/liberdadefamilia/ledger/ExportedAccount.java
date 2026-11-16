package br.com.liberdadefamilia.ledger;

import br.com.liberdadefamilia.shared.Money;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** An account as the data export carries it. {@code type} is the enum name ({@code CHECKING}, ...). */
public record ExportedAccount(
        UUID id,
        String name,
        String type,
        Money initialBalance,
        Money balance,
        @Nullable UUID ownerMembershipId,
        @Nullable Instant archivedAt) {}
