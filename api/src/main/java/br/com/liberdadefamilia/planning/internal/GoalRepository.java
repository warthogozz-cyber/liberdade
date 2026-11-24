package br.com.liberdadefamilia.planning.internal;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The family's goals and their contributions. Every query filters by the family (ADR-0008). */
@Component
class GoalRepository {

    private static final String COLUMNS = """
            id, name, icon, target_amount, currency, due_on, linked_account_id, achieved_at, version""";

    private final JdbcClient jdbc;

    GoalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A goal that is not archived. {@code dueOn} is the first day of the due month. */
    record Row(
            UUID id,
            String name,
            String icon,
            BigDecimal targetAmount,
            String currency,
            @Nullable LocalDate dueOn,
            @Nullable UUID linkedAccountId,
            @Nullable Instant achievedAt,
            long version) {}

    record ContributionRow(
            UUID id,
            BigDecimal amount,
            String currency,
            LocalDate occurredOn,
            @Nullable UUID memberId) {}

    /** Holds the family's goal lock until the transaction ends, so two creations count one after the other (D11). */
    void lockFamily(UUID workspaceId) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:workspaceId::text, 0))")
                .param("workspaceId", workspaceId)
                .query()
                .listOfRows();
    }

    /** The goals that count against the Grátis limit: neither archived nor reached. */
    int countActive(UUID workspaceId) {
        return jdbc.sql("""
                        select count(*) from goals
                        where workspace_id = :workspaceId and archived_at is null and achieved_at is null""")
                .param("workspaceId", workspaceId)
                .query(Integer.class)
                .single();
    }

    UUID insert(
            UUID workspaceId,
            String name,
            BigDecimal targetAmount,
            String currency,
            @Nullable LocalDate dueOn,
            String icon,
            @Nullable UUID linkedAccountId,
            UUID createdBy) {
        return jdbc.sql("""
                        insert into goals
                            (workspace_id, name, target_amount, currency, due_on, icon, linked_account_id, created_by)
                        values (:workspaceId, :name, :target, :currency, :dueOn, :icon, :linked, :createdBy)
                        returning id""")
                .param("workspaceId", workspaceId)
                .param("name", name)
                .param("target", targetAmount)
                .param("currency", currency)
                .param("dueOn", dueOn)
                .param("icon", icon)
                .param("linked", linkedAccountId)
                .param("createdBy", createdBy)
                .query(UUID.class)
                .single();
    }

    /** The goal when the family has it and it is not archived. */
    Optional<Row> find(UUID workspaceId, UUID id) {
        return jdbc.sql("select " + COLUMNS + """
                         from goals
                        where workspace_id = :workspaceId and id = :id and archived_at is null""")
                .param("workspaceId", workspaceId)
                .param("id", id)
                .query(GoalRepository::row)
                .optional();
    }

    /** Like {@link #find}, and holds the row until the transaction ends, so contributions add up one at a time. */
    Optional<Row> findForUpdate(UUID workspaceId, UUID id) {
        return jdbc.sql("select " + COLUMNS + """
                         from goals
                        where workspace_id = :workspaceId and id = :id and archived_at is null
                        for update""")
                .param("workspaceId", workspaceId)
                .param("id", id)
                .query(GoalRepository::row)
                .optional();
    }

    /** Whether the family has the goal, archived or not. */
    boolean exists(UUID workspaceId, UUID id) {
        return jdbc.sql("select exists (select 1 from goals where workspace_id = :workspaceId and id = :id)")
                .param("workspaceId", workspaceId)
                .param("id", id)
                .query(Boolean.class)
                .single();
    }

    /** The goals that are not archived: active ones by creation, then reached ones, latest first. */
    List<Row> list(UUID workspaceId) {
        return jdbc.sql("select " + COLUMNS + """
                         from goals
                        where workspace_id = :workspaceId and archived_at is null
                        order by achieved_at is not null,
                                 case when achieved_at is null then created_at end,
                                 achieved_at desc,
                                 id""")
                .param("workspaceId", workspaceId)
                .query(GoalRepository::row)
                .list();
    }

    /** The goals neither archived nor reached that follow one of the accounts. */
    List<Row> linkedUnreached(UUID workspaceId, Collection<UUID> accountIds) {
        return jdbc.sql("select " + COLUMNS + """
                         from goals
                        where workspace_id = :workspaceId
                          and linked_account_id in (:accountIds)
                          and archived_at is null
                          and achieved_at is null""")
                .param("workspaceId", workspaceId)
                .param("accountIds", accountIds)
                .query(GoalRepository::row)
                .list();
    }

    /** Changes what the user may change, when the version is the goal's; bumps it. */
    boolean update(
            UUID workspaceId,
            UUID id,
            String name,
            BigDecimal targetAmount,
            @Nullable LocalDate dueOn,
            String icon,
            long version,
            Instant at) {
        return jdbc.sql("""
                        update goals
                        set name = :name, target_amount = :target, due_on = :dueOn, icon = :icon,
                            updated_at = :at, version = version + 1
                        where workspace_id = :workspaceId and id = :id and archived_at is null
                          and version = :version""")
                        .param("workspaceId", workspaceId)
                        .param("id", id)
                        .param("name", name)
                        .param("target", targetAmount)
                        .param("dueOn", dueOn)
                        .param("icon", icon)
                        .param("version", version)
                        .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
                        .update()
                > 0;
    }

    /** Archives the goal; false when the family has no such goal or it already was. */
    boolean archive(UUID workspaceId, UUID id, Instant at) {
        return jdbc.sql("""
                        update goals
                        set archived_at = :at, updated_at = :at
                        where workspace_id = :workspaceId and id = :id and archived_at is null""")
                        .param("workspaceId", workspaceId)
                        .param("id", id)
                        .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
                        .update()
                > 0;
    }

    /**
     * Marks the goal reached, unless it already is (D9): a repeated call, or two at once, write it only once. It is a
     * fact of the system, so the version stays, and nobody editing the goal gets a conflict for it.
     */
    boolean markReached(UUID workspaceId, UUID id, Instant at) {
        return jdbc.sql("""
                        update goals
                        set achieved_at = :at
                        where workspace_id = :workspaceId and id = :id and achieved_at is null""")
                        .param("workspaceId", workspaceId)
                        .param("id", id)
                        .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
                        .update()
                > 0;
    }

    /** The sum of the goal's contributions, zero when it has none. */
    BigDecimal contributionSum(UUID workspaceId, UUID goalId) {
        return jdbc.sql("""
                        select coalesce(sum(amount), 0) from goal_contributions
                        where workspace_id = :workspaceId and goal_id = :goalId""")
                .param("workspaceId", workspaceId)
                .param("goalId", goalId)
                .query(BigDecimal.class)
                .single();
    }

    /** The sum of the contributions of each of the family's goals that has any. */
    Map<UUID, BigDecimal> contributionSums(UUID workspaceId) {
        Map<UUID, BigDecimal> sums = new HashMap<>();
        jdbc.sql("""
                        select goal_id, sum(amount) as total from goal_contributions
                        where workspace_id = :workspaceId
                        group by goal_id""").param("workspaceId", workspaceId).query((ResultSet rs) -> {
            sums.put(rs.getObject("goal_id", UUID.class), rs.getBigDecimal("total"));
        });
        return sums;
    }

    ContributionRow insertContribution(
            UUID workspaceId, UUID goalId, BigDecimal amount, LocalDate occurredOn, UUID memberId, String currency) {
        UUID id = jdbc.sql("""
                        insert into goal_contributions (workspace_id, goal_id, amount, occurred_on, member_id)
                        values (:workspaceId, :goalId, :amount, :occurredOn, :memberId)
                        returning id""")
                .param("workspaceId", workspaceId)
                .param("goalId", goalId)
                .param("amount", amount)
                .param("occurredOn", occurredOn)
                .param("memberId", memberId)
                .query(UUID.class)
                .single();
        return new ContributionRow(id, amount, currency, occurredOn, memberId);
    }

    /** The goal's contributions, newest first. */
    List<ContributionRow> contributions(UUID workspaceId, UUID goalId) {
        return jdbc.sql("""
                        select c.id, c.amount, g.currency, c.occurred_on, c.member_id
                        from goal_contributions c
                        join goals g on g.workspace_id = c.workspace_id and g.id = c.goal_id
                        where c.workspace_id = :workspaceId and c.goal_id = :goalId
                        order by c.occurred_on desc, c.created_at desc, c.id desc""")
                .param("workspaceId", workspaceId)
                .param("goalId", goalId)
                .query((rs, n) -> new ContributionRow(
                        rs.getObject("id", UUID.class),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getObject("occurred_on", LocalDate.class),
                        rs.getObject("member_id", UUID.class)))
                .list();
    }

    private static Row row(ResultSet rs, int n) throws SQLException {
        OffsetDateTime achieved = rs.getObject("achieved_at", OffsetDateTime.class);
        return new Row(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("icon"),
                rs.getBigDecimal("target_amount"),
                rs.getString("currency"),
                rs.getObject("due_on", LocalDate.class),
                rs.getObject("linked_account_id", UUID.class),
                achieved == null ? null : achieved.toInstant(),
                rs.getLong("version"));
    }
}
