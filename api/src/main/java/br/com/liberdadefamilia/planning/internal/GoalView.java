package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A goal as the family sees it. {@code saved} is the linked account's balance, or the sum of the contributions, never
 * below zero; {@code percent} is how much of the target that is, rounded down and at most 100. {@code
 * suggestedMonthly} is what to put aside each month to reach it by the due month: none without one or once reached.
 * {@code linkedAccountId} is always null for the youth, who sees only that the goal follows an account.
 */
record GoalView(
        UUID id,
        String name,
        String icon,
        Money targetAmount,
        Money saved,
        int percent,
        @Nullable @Schema(nullable = true) YearMonth dueOn,

        @Nullable @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Money suggestedMonthly,

        boolean linked,
        @Nullable @Schema(nullable = true) UUID linkedAccountId,
        @Nullable @Schema(nullable = true) Instant achievedAt,
        long version) {}
