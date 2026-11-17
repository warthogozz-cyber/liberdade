package br.com.liberdadefamilia.billing;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * The plan a family is on right now. {@code endsAt} is null on {@link PlanKey#FREE}; {@code founder} marks the
 * Família Fundadora plan.
 */
public record PlanView(PlanKey key, @Nullable Instant endsAt, boolean founder) {}
