package br.com.liberdadefamilia.ledger;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An account changed owner, or lost or gained one. Carries the ids only. The former owner is named so that whoever
 * listens can tell them they lost sight of the account. Published inside the transaction that made the change.
 */
public record AccountOwnerChanged(
        UUID workspaceId,
        UUID accountId,
        @Nullable UUID formerOwnerMembershipId,
        @Nullable UUID ownerMembershipId) {}
