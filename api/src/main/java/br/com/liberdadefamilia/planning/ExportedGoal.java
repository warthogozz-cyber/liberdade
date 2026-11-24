package br.com.liberdadefamilia.planning;

import br.com.liberdadefamilia.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A goal as the data export carries it. {@code dueOn} is the first day of the month the goal is due. */
public record ExportedGoal(
        UUID id,
        String name,
        Money target,
        @Nullable LocalDate dueOn,
        String icon,
        @Nullable UUID linkedAccountId,
        @Nullable Instant achievedAt,
        @Nullable Instant archivedAt,
        @Nullable UUID createdBy,
        Instant createdAt) {}
