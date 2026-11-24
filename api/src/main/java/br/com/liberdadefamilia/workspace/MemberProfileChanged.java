package br.com.liberdadefamilia.workspace;

import java.util.UUID;

/** A member's nickname changed. Published inside the transaction that made the change; it carries no name. */
public record MemberProfileChanged(UUID workspaceId, UUID membershipId) {}
