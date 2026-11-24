package br.com.liberdadefamilia.planning;

import java.util.UUID;

/** The family changed how many months the emergency reserve should cover. Published only when the target changes. */
public record ReserveTargetChanged(UUID workspaceId, UUID actorUserId) {}
