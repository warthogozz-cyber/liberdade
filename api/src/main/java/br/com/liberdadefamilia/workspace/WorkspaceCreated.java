package br.com.liberdadefamilia.workspace;

import java.util.Currency;
import java.util.UUID;

/**
 * A family was just created. Published inside the creating transaction: a synchronous listener that fails rolls the
 * family back with it.
 */
public record WorkspaceCreated(UUID workspaceId, UUID ownerMembershipId, Currency baseCurrency) {}
