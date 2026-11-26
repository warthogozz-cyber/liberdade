package br.com.liberdadefamilia.markets;

import java.util.UUID;

/** The family added a market of its own. Published in the transaction that wrote it; carries ids only. */
public record MarketAdded(UUID workspaceId, UUID marketId, UUID actorUserId) {}
