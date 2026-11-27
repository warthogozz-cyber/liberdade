package br.com.liberdadefamilia.markets;

import java.util.UUID;

/**
 * The family joined two products into one. Published in the transaction that wrote it. It carries only ids: the
 * removed product no longer exists, and no name, quantity or price travels in it.
 */
public record ProductsMerged(UUID workspaceId, UUID keptProductId, UUID removedProductId, UUID actorUserId) {}
