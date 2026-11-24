package br.com.liberdadefamilia.ledger;

import java.util.UUID;

/** A tag was renamed. Published inside the transaction that made the change. */
public record TagChanged(UUID workspaceId, UUID tagId) {}
