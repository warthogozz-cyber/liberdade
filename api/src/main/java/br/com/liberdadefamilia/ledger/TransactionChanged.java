package br.com.liberdadefamilia.ledger;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A transaction was recorded, edited, confirmed, skipped, deleted or restored. It names the accounts it touches, the
 * destination too when it is a transfer, and is published inside the transaction that made the change.
 */
public record TransactionChanged(
        UUID workspaceId,
        UUID transactionId,
        UUID accountId,
        @Nullable UUID toAccountId) {}
