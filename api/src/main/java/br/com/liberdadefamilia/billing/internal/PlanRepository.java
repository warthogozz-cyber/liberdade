package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.domain.FounderTheme;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The workspace_plans table: at most one row per family. */
@Component
class PlanRepository {

    /** One stored plan. */
    record PlanRow(
            UUID workspaceId,
            boolean founder,
            @Nullable UUID sourceOrderId,
            Instant startsAt,
            Instant endsAt,
            @Nullable String theme) {}

    private final JdbcClient jdbc;

    PlanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<PlanRow> find(UUID ws) {
        return jdbc.sql("""
                        select workspace_id, founder, source_order_id, starts_at, ends_at, theme
                        from workspace_plans where workspace_id = :ws""")
                .param("ws", ws)
                .query((rs, n) -> new PlanRow(
                        rs.getObject("workspace_id", UUID.class),
                        rs.getBoolean("founder"),
                        rs.getObject("source_order_id", UUID.class),
                        rs.getTimestamp("starts_at").toInstant(),
                        rs.getTimestamp("ends_at").toInstant(),
                        rs.getString("theme")))
                .optional();
    }

    /**
     * Gives the family the founder plan unless it already has one running at {@code startsAt}. Returns whether it
     * wrote; an ended plan is replaced, and the family keeps the theme it had picked.
     */
    boolean activateIfFree(UUID ws, UUID orderId, Instant startsAt, Instant endsAt) {
        return jdbc.sql("""
                                insert into workspace_plans (workspace_id, plan_key, founder, source_order_id, starts_at, ends_at)
                                values (:ws, 'FAMILY', true, :order, :startsAt, :endsAt)
                                on conflict (workspace_id) do update
                                set founder = true, source_order_id = excluded.source_order_id,
                                    starts_at = excluded.starts_at, ends_at = excluded.ends_at, updated_at = now()
                                where workspace_plans.ends_at <= excluded.starts_at""")
                        .param("ws", ws)
                        .param("order", orderId)
                        .param("startsAt", Timestamp.from(startsAt))
                        .param("endsAt", Timestamp.from(endsAt))
                        .update()
                > 0;
    }

    /** Ends the plan the order bought, if that is still the family's plan. */
    void endNow(UUID ws, UUID orderId, Instant now) {
        jdbc.sql("""
                        update workspace_plans set ends_at = :now, updated_at = now()
                        where workspace_id = :ws and source_order_id = :order""")
                .param("ws", ws)
                .param("order", orderId)
                .param("now", Timestamp.from(now))
                .update();
    }

    void setTheme(UUID ws, FounderTheme theme) {
        jdbc.sql("update workspace_plans set theme = :theme, updated_at = now() where workspace_id = :ws")
                .param("ws", ws)
                .param("theme", theme.name())
                .update();
    }
}
