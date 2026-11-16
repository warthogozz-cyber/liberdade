package br.com.liberdadefamilia.workspace;

import java.util.UUID;

/** A link was made. Published inside the transaction that stores it; it never carries the token. */
public record InvitationCreated(
        UUID workspaceId, UUID invitationId, InvitationKind kind, Role role, UUID actorUserId) {}
