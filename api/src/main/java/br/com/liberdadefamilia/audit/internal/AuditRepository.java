package br.com.liberdadefamilia.audit.internal;

import br.com.liberdadefamilia.audit.AuditEntry;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The audit table. {@code occurred_at} is filled by the database when a row is written. */
@Component
class AuditRepository {

    private static final TypeReference<Map<String, String>> STRINGS = new TypeReference<>() {};

    /** One stored row, as read for a person's history. */
    record Row(
            UUID id,
            String action,
            Instant occurredAt,
            @Nullable UUID workspaceId,
            @Nullable UUID actorUserId,
            Map<String, String> metadata) {}

    private final JdbcClient jdbc;
    private final JsonMapper json;

    AuditRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void insert(AuditEntry entry) {
        jdbc.sql("""
                        insert into audit_events (workspace_id, actor_user_id, action, target_type, target_id, metadata)
                        values (:workspaceId, :actorUserId, :action, :targetType, :targetId, cast(:metadata as jsonb))""")
                .param("workspaceId", entry.workspaceId())
                .param("actorUserId", entry.actorUserId())
                .param("action", entry.action())
                .param("targetType", entry.targetType())
                .param("targetId", entry.targetId())
                .param("metadata", json.writeValueAsString(new LinkedHashMap<>(entry.metadata())))
                .update();
    }

    /**
     * What the person did, what was done to them and what was done to their memberships, since the instant. Every
     * row when {@code limit} is null.
     */
    List<Row> activityOf(UUID userId, Collection<UUID> membershipIds, OffsetDateTime since, @Nullable Integer limit) {
        String viaMembership =
                membershipIds.isEmpty() ? "" : " or (target_type = 'MEMBERSHIP' and target_id in (:membershipIds))";
        String sql = """
                select id, action, occurred_at, workspace_id, actor_user_id, metadata::text as metadata
                from audit_events
                where occurred_at >= :since
                  and (actor_user_id = :userId or (target_type = 'USER' and target_id = :userId)""" + viaMembership + ")\n order by occurred_at desc, id desc"
                + (limit == null ? "" : "\n limit :limit");
        JdbcClient.StatementSpec statement = jdbc.sql(sql).param("since", since).param("userId", userId);
        if (limit != null) {
            statement = statement.param("limit", limit);
        }
        if (!membershipIds.isEmpty()) {
            statement = statement.param("membershipIds", membershipIds);
        }
        return statement
                .query((rs, row) -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getString("action"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getObject("actor_user_id", UUID.class),
                        json.readValue(rs.getString("metadata"), STRINGS)))
                .list();
    }

    int purgeBefore(OffsetDateTime cutoff) {
        return jdbc.sql("delete from audit_events where occurred_at < :cutoff")
                .param("cutoff", cutoff)
                .update();
    }
}
