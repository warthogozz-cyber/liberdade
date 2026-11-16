package br.com.liberdadefamilia.workspace;

import java.util.UUID;

/** A recovery link brought a member back into their account. Published inside the transaction that uses the link. */
public record RecoveryUsed(UUID workspaceId, UUID targetMembershipId, UUID userId) {}
