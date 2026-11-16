package br.com.liberdadefamilia.audit;

import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One sensitive action to record. {@code targetType} is one of USER, MEMBERSHIP, INVITATION, WORKSPACE, MODULE or
 * CONSENT. {@code metadata} holds fixed labels only: never a name, a value, a description or an address.
 */
public record AuditEntry(
        @Nullable UUID workspaceId,
        @Nullable UUID actorUserId,
        String action,
        String targetType,
        @Nullable UUID targetId,
        Map<String, String> metadata) {}
