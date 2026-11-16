package br.com.liberdadefamilia.workspace;

import java.util.UUID;

/** A pending link was cancelled. Published once, when it is cancelled, not on a repeat. */
public record InvitationRevoked(UUID workspaceId, UUID invitationId, UUID actorUserId) {}
