package br.com.liberdadefamilia.ventures;

import java.util.UUID;

/** A project was closed. Published in the transaction that closes it. */
public record VentureClosed(UUID workspaceId, UUID ventureId, UUID actorUserId) {}
