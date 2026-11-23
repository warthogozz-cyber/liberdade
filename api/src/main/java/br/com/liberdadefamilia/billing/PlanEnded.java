package br.com.liberdadefamilia.billing;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The family's plan was cut short. The reason is a fixed label: REFUND, CHARGEBACK or CUSTOMER_REFUND. */
public record PlanEnded(UUID workspaceId, @Nullable UUID orderId, String reason) {}
