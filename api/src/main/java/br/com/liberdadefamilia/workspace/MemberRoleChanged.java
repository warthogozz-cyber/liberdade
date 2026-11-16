package br.com.liberdadefamilia.workspace;

import java.util.UUID;

/** A member's role changed. Published inside the changing transaction. */
public record MemberRoleChanged(UUID workspaceId, UUID membershipId, Role from, Role to, UUID actorUserId) {}
