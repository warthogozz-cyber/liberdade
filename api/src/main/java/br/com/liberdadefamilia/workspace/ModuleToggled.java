package br.com.liberdadefamilia.workspace;

import java.util.UUID;

/** A module was switched on or off. Published inside the changing transaction, only when the stored value changed. */
public record ModuleToggled(UUID workspaceId, ModuleKey key, boolean enabled, UUID actorUserId) {}
