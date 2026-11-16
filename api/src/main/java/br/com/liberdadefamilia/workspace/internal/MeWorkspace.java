package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.Role;
import java.util.UUID;

/** A family the user is an active member of, as {@code GET /v1/me} lists it. */
record MeWorkspace(UUID id, String name, Role role, UUID membershipId, String timezone, String currency) {}
