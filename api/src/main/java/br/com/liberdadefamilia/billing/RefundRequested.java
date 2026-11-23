package br.com.liberdadefamilia.billing;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A payment is to be given back. Published inside the transaction that marks the order. */
public record RefundRequested(
        @Nullable UUID workspaceId,
        UUID orderId,
        RefundReason reason,
        @Nullable UUID actorUserId) {}
