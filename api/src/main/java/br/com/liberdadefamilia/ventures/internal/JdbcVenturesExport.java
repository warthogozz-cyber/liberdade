package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.ventures.ExportedVenture;
import br.com.liberdadefamilia.ventures.ExportedVentureItem;
import br.com.liberdadefamilia.ventures.ExportedVentureProduct;
import br.com.liberdadefamilia.ventures.ExportedVentureStep;
import br.com.liberdadefamilia.ventures.VenturesExport;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads the projects, with their items, products and steps, for the data export. */
@Component
class JdbcVenturesExport implements VenturesExport {

    private static final String VENTURES = """
            select id, name, type, status, started_on, closed_on, currency, template_key
            from ventures
            where workspace_id = :workspaceId
            """;

    private static final String ITEMS = """
            select venture_id, id, kind, name, amount, currency, position
            from venture_cost_items
            where workspace_id = :workspaceId
            order by position, id""";

    private static final String PRODUCTS = """
            select venture_id, id, name, unit_variable_cost, unit_price, planned_units_month, currency, position
            from venture_products
            where workspace_id = :workspaceId
            order by position, id""";

    private static final String STEPS = """
            select venture_id, id, title, position, done_at
            from venture_steps
            where workspace_id = :workspaceId
            order by position, id""";

    private final JdbcClient jdbc;

    JdbcVenturesExport(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ExportedVenture> ventures(UUID workspaceId) {
        return assemble(
                workspaceId,
                jdbc.sql(VENTURES + "order by created_at, id")
                        .param("workspaceId", workspaceId)
                        .query(JdbcVenturesExport::head)
                        .list());
    }

    @Override
    public List<ExportedVenture> venturesCreatedBy(UUID workspaceId, UUID membershipId) {
        return assemble(
                workspaceId,
                jdbc.sql(VENTURES + "and created_by = :membershipId order by created_at, id")
                        .param("workspaceId", workspaceId)
                        .param("membershipId", membershipId)
                        .query(JdbcVenturesExport::head)
                        .list());
    }

    @Override
    public boolean anyStartedBy(UUID workspaceId, UUID membershipId) {
        return jdbc.sql("""
                        select exists (
                            select 1 from ventures
                            where workspace_id = :workspaceId and created_by = :membershipId
                              and started_on is not null)""")
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .query(Boolean.class)
                .single();
    }

    private List<ExportedVenture> assemble(UUID workspaceId, List<Head> heads) {
        if (heads.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ExportedVentureItem>> items = children(
                workspaceId,
                ITEMS,
                (rs, row) -> new ExportedVentureItem(
                        rs.getObject("id", UUID.class),
                        rs.getString("kind"),
                        rs.getString("name"),
                        money(rs, "amount"),
                        rs.getInt("position")));
        Map<UUID, List<ExportedVentureProduct>> products = children(
                workspaceId,
                PRODUCTS,
                (rs, row) -> new ExportedVentureProduct(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        money(rs, "unit_variable_cost"),
                        money(rs, "unit_price"),
                        rs.getInt("planned_units_month"),
                        rs.getInt("position")));
        Map<UUID, List<ExportedVentureStep>> steps = children(workspaceId, STEPS, (rs, row) -> {
            Timestamp doneAt = rs.getTimestamp("done_at");
            return new ExportedVentureStep(
                    rs.getObject("id", UUID.class),
                    rs.getString("title"),
                    rs.getInt("position"),
                    doneAt == null ? null : doneAt.toInstant());
        });
        return heads.stream()
                .map(h -> new ExportedVenture(
                        h.id(),
                        h.name(),
                        h.type(),
                        h.status(),
                        h.startedOn(),
                        h.closedOn(),
                        h.currency(),
                        h.templateKey(),
                        items.getOrDefault(h.id(), List.of()),
                        products.getOrDefault(h.id(), List.of()),
                        steps.getOrDefault(h.id(), List.of())))
                .toList();
    }

    /** The rows of a child table of the family, grouped by project, in the order of the query. */
    private <T> Map<UUID, List<T>> children(UUID workspaceId, String sql, RowMapper<T> mapper) {
        Map<UUID, List<T>> byVenture = new HashMap<>();
        jdbc.sql(sql).param("workspaceId", workspaceId).query(rs -> {
            byVenture
                    .computeIfAbsent(rs.getObject("venture_id", UUID.class), id -> new ArrayList<>())
                    .add(mapper.map(rs, rs.getRow()));
        });
        return byVenture;
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs, int row) throws SQLException;
    }

    private record Head(
            UUID id,
            String name,
            String type,
            String status,
            @Nullable LocalDate startedOn,
            @Nullable LocalDate closedOn,
            String currency,
            @Nullable String templateKey) {}

    private static Head head(ResultSet rs, int row) throws SQLException {
        return new Head(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("type"),
                rs.getString("status"),
                rs.getObject("started_on", LocalDate.class),
                rs.getObject("closed_on", LocalDate.class),
                rs.getString("currency"),
                rs.getString("template_key"));
    }

    private static Money money(ResultSet rs, String column) throws SQLException {
        return Money.of(rs.getBigDecimal(column), Currency.getInstance(rs.getString("currency")));
    }
}
