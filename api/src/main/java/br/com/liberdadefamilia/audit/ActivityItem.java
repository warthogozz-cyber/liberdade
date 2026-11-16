package br.com.liberdadefamilia.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One line of a person's history. {@code actorName} is the actor's nickname in the family of the event or, without
 * one, their name; null when the account is gone.
 */
public record ActivityItem(
        UUID id,
        String action,
        Instant occurredAt,
        @Nullable @Schema(nullable = true) UUID workspaceId,
        @Nullable @Schema(nullable = true) String actorName,
        boolean byMe,
        Map<String, String> metadata) {}
