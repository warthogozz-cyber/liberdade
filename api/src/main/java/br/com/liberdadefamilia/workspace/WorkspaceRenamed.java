package br.com.liberdadefamilia.workspace;

import java.util.UUID;

/** The family got a new name. The event carries no name: a name is personal data and the audit trail keeps none. */
public record WorkspaceRenamed(UUID workspaceId, UUID actorUserId) {}
