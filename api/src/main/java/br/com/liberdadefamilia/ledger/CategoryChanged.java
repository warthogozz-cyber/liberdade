package br.com.liberdadefamilia.ledger;

import java.util.UUID;

/** A category was created, edited, archived or brought back. Published inside the transaction that made the change. */
public record CategoryChanged(UUID workspaceId, UUID categoryId) {}
