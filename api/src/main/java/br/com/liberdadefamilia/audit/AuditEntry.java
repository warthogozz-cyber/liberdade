package br.com.liberdadefamilia.audit;

import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One sensitive action to record. {@code targetType} is one of USER, MEMBERSHIP, INVITATION, WORKSPACE, MODULE,
 * CONSENT, ORDER, PLAN, RECURRING_RULE, GOAL, ACCOUNT, VENTURE, MARKET, RECEIPT or PRODUCT. {@code metadata} holds fixed labels
 * and ids only: never a name, a value, a description, a link, a point or an address.
 */
public record AuditEntry(
        @Nullable UUID workspaceId,
        @Nullable UUID actorUserId,
        String action,
        String targetType,
        @Nullable UUID targetId,
        Map<String, String> metadata) {}
