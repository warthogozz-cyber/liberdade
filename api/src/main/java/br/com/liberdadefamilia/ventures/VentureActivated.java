package br.com.liberdadefamilia.ventures;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A project went from planning to active. Published in the transaction that starts it, not when a paused or closed
 * project is taken up again. The creator is null when they have left the family.
 */
public record VentureActivated(
        UUID workspaceId, UUID ventureId, @Nullable UUID createdByMembershipId) {}
