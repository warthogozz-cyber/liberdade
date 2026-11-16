package br.com.liberdadefamilia.workspace;

import java.util.UUID;

/** An Owner asked for the family to be deleted after the grace. */
public record WorkspaceDeletionRequested(UUID workspaceId, UUID actorUserId) {}
