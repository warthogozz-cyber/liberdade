package br.com.liberdadefamilia.markets;

import java.util.UUID;

/** The family removed the address of its markets. Ids only. */
public record MarketHomeCleared(UUID workspaceId, UUID actorUserId) {}
