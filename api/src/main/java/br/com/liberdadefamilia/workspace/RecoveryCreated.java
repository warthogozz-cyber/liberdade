package br.com.liberdadefamilia.workspace;

import java.util.UUID;

/** A recovery link was made for a member. Published inside the transaction that stores it; it never carries the token. */
public record RecoveryCreated(UUID workspaceId, UUID invitationId, UUID targetMembershipId, UUID actorUserId) {}
