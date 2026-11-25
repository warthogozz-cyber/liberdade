package br.com.liberdadefamilia.markets;

import java.util.UUID;

/** The family set the address its markets are searched from. Ids only: no point and no label travels in it. */
public record MarketHomeSet(UUID workspaceId, UUID actorUserId) {}
