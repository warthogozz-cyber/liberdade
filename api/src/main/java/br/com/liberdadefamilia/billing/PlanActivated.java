package br.com.liberdadefamilia.billing;

import java.time.Instant;
import java.util.UUID;

/** A payment gave the family the Founder plan. Published inside the transaction that stores it. */
public record PlanActivated(UUID workspaceId, UUID orderId, Instant endsAt) {}
