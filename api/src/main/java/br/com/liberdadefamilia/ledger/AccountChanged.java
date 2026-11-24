package br.com.liberdadefamilia.ledger;

import java.util.UUID;

/**
 * An account was edited (its balance included), archived or unarchived. Published inside the transaction that made the
 * change.
 */
public record AccountChanged(UUID workspaceId, UUID accountId) {}
