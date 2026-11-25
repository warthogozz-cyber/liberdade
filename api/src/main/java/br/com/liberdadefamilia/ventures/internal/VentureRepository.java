package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.ventures.domain.VentureStatus;
import br.com.liberdadefamilia.ventures.domain.VentureType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The family's projects with their cost lines, products and steps. Every query filters by the family (ADR-0008). */
@Component
class VentureRepository {

    static final String INITIAL = "INITIAL";
    static final String FIXED_MONTHLY = "FIXED_MONTHLY";

    private static final String COLUMNS =
            "id, name, type, template_key, status, started_on, closed_on, currency, version";

    private final JdbcClient jdbc;

    VentureRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Holds the family's project lock until the transaction ends, so two creations count one after the other (D7). */
    void lockFamily(UUID workspaceId) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", "ventures:" + workspaceId)
                .query()
                .listOfRows();
    }

    /** The projects that count against the Grátis limit: every one that is not closed. */
    int countOpen(UUID workspaceId) {
        return jdbc.sql("select count(*) from ventures where workspace_id = :workspaceId and status <> 'CLOSED'")
                .param("workspaceId", workspaceId)
                .query(Integer.class)
                .single();
    }

    UUID insert(
            UUID workspaceId,
            String name,
            VentureType type,
            @Nullable String templateKey,
            String currency,
            UUID createdBy) {
        return jdbc.sql("""
                        insert into ventures (workspace_id, name, type, template_key, currency, created_by)
                        values (:workspaceId, :name, :type, :templateKey, :currency, :createdBy)
                        returning id""")
                .param("workspaceId", workspaceId)
                .param("name", name)
                .param("type", type.name())
                .param("templateKey", templateKey)
                .param("currency", currency)
                .param("createdBy", createdBy)
                .query(UUID.class)
                .single();
    }

    /** Inserts the line; a given id is kept, otherwise the database makes one. */
    void insertItem(
            UUID workspaceId,
            UUID ventureId,
            String kind,
            @Nullable UUID id,
            String name,
            BigDecimal amount,
            String currency,
            boolean isExample,
            int position) {
        jdbc.sql("""
                        insert into venture_cost_items
                            (id, workspace_id, venture_id, kind, name, amount, currency, is_example, position)
                        values (coalesce(cast(:id as uuid), uuidv7()), :workspaceId, :ventureId, :kind, :name,
                                :amount, :currency, :isExample, :position)""")
                .param("id", id, Types.OTHER)
                .param("workspaceId", workspaceId)
                .param("ventureId", ventureId)
                .param("kind", kind)
                .param("name", name)
                .param("amount", amount)
                .param("currency", currency)
                .param("isExample", isExample)
                .param("position", position)
                .update();
    }

    /** Inserts the product; a given id is kept, otherwise the database makes one. */
    void insertProduct(
            UUID workspaceId,
            UUID ventureId,
            @Nullable UUID id,
            String name,
            BigDecimal unitVariableCost,
            BigDecimal unitPrice,
            int plannedUnitsMonth,
            String currency,
            boolean isExample,
            int position) {
        jdbc.sql("""
                        insert into venture_products
                            (id, workspace_id, venture_id, name, unit_variable_cost, unit_price, planned_units_month,
                             currency, is_example, position)
                        values (coalesce(cast(:id as uuid), uuidv7()), :workspaceId, :ventureId, :name, :cost, :price,
                                :units, :currency, :isExample, :position)""")
                .param("id", id, Types.OTHER)
                .param("workspaceId", workspaceId)
                .param("ventureId", ventureId)
                .param("name", name)
                .param("cost", unitVariableCost)
                .param("price", unitPrice)
                .param("units", plannedUnitsMonth)
                .param("currency", currency)
                .param("isExample", isExample)
                .param("position", position)
                .update();
    }

    void insertStep(UUID workspaceId, UUID ventureId, String title, int position) {
        jdbc.sql("""
                        insert into venture_steps (workspace_id, venture_id, title, position)
                        values (:workspaceId, :ventureId, :title, :position)""")
                .param("workspaceId", workspaceId)
                .param("ventureId", ventureId)
                .param("title", title)
                .param("position", position)
                .update();
    }

    /** The project's steps in position order. */
    List<StoredVenture.Step> steps(UUID workspaceId, UUID ventureId) {
        return jdbc.sql("""
                        select id, title, position, done_at from venture_steps
                        where workspace_id = :workspaceId and venture_id = :ventureId
                        order by position, id""")
                .param("workspaceId", workspaceId)
                .param("ventureId", ventureId)
                .query((rs, row) -> step(rs))
                .list();
    }

    /** Adds the step at the given position and returns it. */
    StoredVenture.Step addStep(UUID workspaceId, UUID ventureId, String title, int position) {
        return jdbc.sql("""
                        insert into venture_steps (workspace_id, venture_id, title, position)
                        values (:workspaceId, :ventureId, :title, :position)
                        returning id, title, position, done_at""")
                .param("workspaceId", workspaceId)
                .param("ventureId", ventureId)
                .param("title", title)
                .param("position", position)
                .query((rs, row) -> step(rs))
                .single();
    }

    /** Writes the step's title and done time. */
    void updateStep(UUID workspaceId, UUID ventureId, UUID stepId, String title, @Nullable Instant doneAt) {
        jdbc.sql("""
                        update venture_steps set title = :title, done_at = :doneAt
                        where workspace_id = :workspaceId and venture_id = :ventureId and id = :stepId""")
                .param("workspaceId", workspaceId)
                .param("ventureId", ventureId)
                .param("stepId", stepId)
                .param("title", title)
                .param(
                        "doneAt",
                        doneAt == null ? null : OffsetDateTime.ofInstant(doneAt, ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .update();
    }

    /** Gives the steps the positions 0..n-1 in the order they come. */
    void renumberSteps(UUID workspaceId, UUID ventureId, List<UUID> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            jdbc.sql("""
                            update venture_steps set position = :position
                            where workspace_id = :workspaceId and venture_id = :ventureId and id = :stepId
                              and position <> :position""")
                    .param("workspaceId", workspaceId)
                    .param("ventureId", ventureId)
                    .param("stepId", ordered.get(i))
                    .param("position", i)
                    .update();
        }
    }

    boolean deleteStep(UUID workspaceId, UUID ventureId, UUID stepId) {
        return jdbc.sql("""
                        delete from venture_steps
                        where workspace_id = :workspaceId and venture_id = :ventureId and id = :stepId""")
                        .param("workspaceId", workspaceId)
                        .param("ventureId", ventureId)
                        .param("stepId", stepId)
                        .update()
                > 0;
    }

    /** Removes every cost line and product of the project, so the caller can write the new ones. */
    void clearLines(UUID workspaceId, UUID ventureId) {
        jdbc.sql("delete from venture_cost_items where workspace_id = :workspaceId and venture_id = :ventureId")
                .param("workspaceId", workspaceId)
                .param("ventureId", ventureId)
                .update();
        jdbc.sql("delete from venture_products where workspace_id = :workspaceId and venture_id = :ventureId")
                .param("workspaceId", workspaceId)
                .param("ventureId", ventureId)
                .update();
    }

    /** The project when the family has it, with its lines and steps. */
    Optional<StoredVenture> find(UUID workspaceId, UUID id) {
        return load(workspaceId, id).stream().findFirst();
    }

    /** The family's projects, oldest first. */
    List<StoredVenture> list(UUID workspaceId) {
        return load(workspaceId, null);
    }

    /** Whether the family has the project. */
    boolean exists(UUID workspaceId, UUID id) {
        return jdbc.sql("select exists (select 1 from ventures where workspace_id = :workspaceId and id = :id)")
                .param("workspaceId", workspaceId)
                .param("id", id)
                .query(Boolean.class)
                .single();
    }

    /** Changes the fields the user edits, when the version is the project's; bumps it. */
    boolean update(
            UUID workspaceId,
            UUID id,
            String name,
            VentureType type,
            @Nullable LocalDate startedOn,
            long version,
            Instant at) {
        return jdbc.sql("""
                        update ventures
                        set name = :name, type = :type, started_on = :startedOn,
                            updated_at = :at, version = version + 1
                        where workspace_id = :workspaceId and id = :id and version = :version""")
                        .param("workspaceId", workspaceId)
                        .param("id", id)
                        .param("name", name)
                        .param("type", type.name())
                        .param("startedOn", startedOn)
                        .param("version", version)
                        .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
                        .update()
                > 0;
    }

    /** Writes the status and its days, when the version is the project's; bumps it. */
    boolean changeStatus(
            UUID workspaceId,
            UUID id,
            VentureStatus status,
            @Nullable LocalDate startedOn,
            @Nullable LocalDate closedOn,
            long version,
            Instant at) {
        return jdbc.sql("""
                        update ventures
                        set status = :status, started_on = :startedOn, closed_on = :closedOn,
                            updated_at = :at, version = version + 1
                        where workspace_id = :workspaceId and id = :id and version = :version""")
                        .param("workspaceId", workspaceId)
                        .param("id", id)
                        .param("status", status.name())
                        .param("startedOn", startedOn)
                        .param("closedOn", closedOn)
                        .param("version", version)
                        .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
                        .update()
                > 0;
    }

    /** Deletes the project; its lines and steps go with it. False when the family has no such project. */
    boolean delete(UUID workspaceId, UUID id) {
        return jdbc.sql("delete from ventures where workspace_id = :workspaceId and id = :id")
                        .param("workspaceId", workspaceId)
                        .param("id", id)
                        .update()
                > 0;
    }

    private List<StoredVenture> load(UUID workspaceId, @Nullable UUID id) {
        String only = id == null ? "" : " and venture_id = :id";
        Map<UUID, Header> headers = new LinkedHashMap<>();
        var headerQuery = jdbc.sql("select " + COLUMNS + """
                         from ventures
                        where workspace_id = :workspaceId""" + (id == null ? "" : " and id = :id") + " " + """
                         order by created_at, id""")
                .param("workspaceId", workspaceId);
        if (id != null) {
            headerQuery = headerQuery.param("id", id);
        }
        headerQuery.query((ResultSet rs) -> {
            Header header = new Header(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    VentureType.valueOf(rs.getString("type")),
                    rs.getString("template_key"),
                    VentureStatus.valueOf(rs.getString("status")),
                    rs.getObject("started_on", LocalDate.class),
                    rs.getObject("closed_on", LocalDate.class),
                    rs.getString("currency"),
                    rs.getLong("version"));
            headers.put(header.id(), header);
        });
        if (headers.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<StoredVenture.Item>> initial = new HashMap<>();
        Map<UUID, List<StoredVenture.Item>> fixed = new HashMap<>();
        Map<UUID, List<StoredVenture.Product>> products = new HashMap<>();
        Map<UUID, List<StoredVenture.Step>> steps = new HashMap<>();

        var itemQuery = jdbc.sql("""
                        select venture_id, id, kind, name, amount, is_example from venture_cost_items
                        where workspace_id = :workspaceId""" + only + " order by position, id").param("workspaceId", workspaceId);
        var productQuery = jdbc.sql("""
                        select venture_id, id, name, unit_variable_cost, unit_price, planned_units_month, is_example
                        from venture_products
                        where workspace_id = :workspaceId""" + only + " order by position, id").param("workspaceId", workspaceId);
        var stepQuery = jdbc.sql("""
                        select venture_id, id, title, position, done_at from venture_steps
                        where workspace_id = :workspaceId""" + only + " order by position, id").param("workspaceId", workspaceId);
        if (id != null) {
            itemQuery = itemQuery.param("id", id);
            productQuery = productQuery.param("id", id);
            stepQuery = stepQuery.param("id", id);
        }
        itemQuery.query((ResultSet rs) -> {
            StoredVenture.Item item = new StoredVenture.Item(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getBigDecimal("amount"),
                    rs.getBoolean("is_example"));
            var target = INITIAL.equals(rs.getString("kind")) ? initial : fixed;
            target.computeIfAbsent(rs.getObject("venture_id", UUID.class), key -> new ArrayList<>())
                    .add(item);
        });
        productQuery.query((ResultSet rs) -> {
            products.computeIfAbsent(rs.getObject("venture_id", UUID.class), key -> new ArrayList<>())
                    .add(new StoredVenture.Product(
                            rs.getObject("id", UUID.class),
                            rs.getString("name"),
                            rs.getBigDecimal("unit_variable_cost"),
                            rs.getBigDecimal("unit_price"),
                            rs.getInt("planned_units_month"),
                            rs.getBoolean("is_example")));
        });
        stepQuery.query((ResultSet rs) -> {
            steps.computeIfAbsent(rs.getObject("venture_id", UUID.class), key -> new ArrayList<>())
                    .add(step(rs));
        });
        return headers.values().stream()
                .map(h -> new StoredVenture(
                        h.id(),
                        h.name(),
                        h.type(),
                        h.templateKey(),
                        h.status(),
                        h.startedOn(),
                        h.closedOn(),
                        h.currency(),
                        h.version(),
                        initial.getOrDefault(h.id(), List.of()),
                        fixed.getOrDefault(h.id(), List.of()),
                        products.getOrDefault(h.id(), List.of()),
                        steps.getOrDefault(h.id(), List.of())))
                .toList();
    }

    private static StoredVenture.Step step(ResultSet rs) throws SQLException {
        OffsetDateTime done = rs.getObject("done_at", OffsetDateTime.class);
        return new StoredVenture.Step(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getInt("position"),
                done == null ? null : done.toInstant());
    }

    private record Header(
            UUID id,
            String name,
            VentureType type,
            @Nullable String templateKey,
            VentureStatus status,
            @Nullable LocalDate startedOn,
            @Nullable LocalDate closedOn,
            String currency,
            long version) {}
}
