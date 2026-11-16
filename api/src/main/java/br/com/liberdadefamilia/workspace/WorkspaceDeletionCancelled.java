package br.com.liberdadefamilia.workspace;

import java.util.UUID;

/** An Owner took back the request to delete the family. */
public record WorkspaceDeletionCancelled(UUID workspaceId, UUID actorUserId) {}
