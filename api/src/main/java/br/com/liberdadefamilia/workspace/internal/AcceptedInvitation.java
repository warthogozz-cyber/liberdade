package br.com.liberdadefamilia.workspace.internal;

import java.util.UUID;

/** @param mustRegisterPasskey true when the person was created by this link and has no way back in yet */
record AcceptedInvitation(UUID workspaceId, UUID membershipId, boolean mustRegisterPasskey) {}
