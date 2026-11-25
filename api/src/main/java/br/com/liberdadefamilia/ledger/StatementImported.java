package br.com.liberdadefamilia.ledger;

import java.util.UUID;

/**
 * A member imported a batch of statement lines into an account: how many became new transactions, how many confirmed a
 * planned one and how many were already there. Carries counts only, never a value, date or description. Published
 * inside the transaction that stored the batch, even when nothing was stored.
 */
public record StatementImported(
        UUID workspaceId, UUID accountId, UUID actorUserId, int created, int confirmed, int skipped) {}
