package br.com.liberdadefamilia.workspace;

import java.time.ZoneId;
import java.util.Currency;
import java.util.UUID;

/** The caller's active membership in the family of the current request, checked by the guard. */
public record WorkspaceMember(
        UUID workspaceId, UUID membershipId, UUID userId, Role role, ZoneId timezone, Currency baseCurrency) {}
