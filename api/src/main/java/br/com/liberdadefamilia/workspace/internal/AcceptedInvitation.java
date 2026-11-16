package br.com.liberdadefamilia.workspace.internal;

import java.util.UUID;

/**
 * @param mustRegisterPasskey true when the person has no way back in yet: the link created them, or it was a recovery
 *     link, which signs the target in so that they can register a new passkey
 */
record AcceptedInvitation(UUID workspaceId, UUID membershipId, boolean mustRegisterPasskey) {}
