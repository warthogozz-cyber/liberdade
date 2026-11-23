package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.BillingExport;
import br.com.liberdadefamilia.billing.ExportedOrder;
import br.com.liberdadefamilia.billing.ExportedPlan;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads the billing tables for the data export. */
@Component
class BillingJdbcExport implements BillingExport {

    private final JdbcClient jdbc;

    BillingJdbcExport(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ExportedOrder> orders(UUID workspaceId) {
        return jdbc.sql("""
                        select product, amount_cents, currency, status, created_at, paid_at, refunded_at
                        from billing_orders
                        where workspace_id = :workspaceId
                        order by created_at, id""")
                .param("workspaceId", workspaceId)
                .query((rs, row) -> new ExportedOrder(
                        rs.getString("product"),
                        rs.getLong("amount_cents"),
                        rs.getString("currency"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(),
                        instant(rs.getTimestamp("paid_at")),
                        instant(rs.getTimestamp("refunded_at"))))
                .list();
    }

    @Override
    public Optional<ExportedPlan> plan(UUID workspaceId) {
        return jdbc.sql("""
                        select plan_key, founder, starts_at, ends_at
                        from workspace_plans where workspace_id = :workspaceId""")
                .param("workspaceId", workspaceId)
                .query((rs, row) -> new ExportedPlan(
                        rs.getString("plan_key"),
                        rs.getBoolean("founder"),
                        rs.getTimestamp("starts_at").toInstant(),
                        rs.getTimestamp("ends_at").toInstant()))
                .optional();
    }

    private static @Nullable Instant instant(@Nullable Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
