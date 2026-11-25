package br.com.liberdadefamilia.ventures;

import java.util.UUID;

/** A project was deleted with its items, products and steps. Published in the transaction that deletes it. */
public record VentureDeleted(UUID workspaceId, UUID ventureId, UUID actorUserId) {}
