package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.planning.ExportedBudget;
import br.com.liberdadefamilia.planning.ExportedContribution;
import br.com.liberdadefamilia.planning.ExportedGoal;
import br.com.liberdadefamilia.planning.ExportedRecurringRule;
import br.com.liberdadefamilia.planning.ExportedReserveSettings;
import br.com.liberdadefamilia.planning.PlanningExport;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.PaymentMethod;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads the recurring rules for the data export. */
@Component
class PlanningJdbcExport implements PlanningExport {

    private static final String SELECT = """
            select id, kind, amount, currency, account_id, to_account_id, category_id, payment_method,
                   description, frequency, "interval", start_on, end_on, next_due_on, created_by, version,
                   created_at, archived_at
            from recurring_rules
            """;

    private static final String CONTRIBUTIONS = """
            select c.id, c.goal_id, g.name as goal_name, c.amount, g.currency, c.occurred_on, c.member_id,
                   c.created_at
            from goal_contributions c
            join goals g on g.workspace_id = c.workspace_id and g.id = c.goal_id
            """;

    private final JdbcClient jdbc;

    PlanningJdbcExport(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ExportedRecurringRule> rules(UUID workspaceId) {
        return jdbc.sql(SELECT + """
                        where workspace_id = :workspaceId
                        order by start_on, id""")
                .param("workspaceId", workspaceId)
                .query(PlanningJdbcExport::rule)
                .list();
    }

    @Override
    public List<ExportedRecurringRule> rulesCreatedBy(UUID workspaceId, UUID membershipId) {
        return jdbc.sql(SELECT + """
                        where workspace_id = :workspaceId and created_by = :membershipId
                        order by start_on, id""")
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .query(PlanningJdbcExport::rule)
                .list();
    }

    @Override
    public List<ExportedBudget> budgets(UUID workspaceId) {
        return jdbc.sql("""
                        select id, category_id, month, limit_amount, currency, created_at
                        from budgets
                        where workspace_id = :workspaceId
                        order by month, id""")
                .param("workspaceId", workspaceId)
                .query((rs, row) -> new ExportedBudget(
                        rs.getObject("id", UUID.class),
                        rs.getObject("category_id", UUID.class),
                        rs.getObject("month", LocalDate.class),
                        money(rs, "limit_amount"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    @Override
    public List<ExportedGoal> goals(UUID workspaceId) {
        return jdbc.sql("""
                        select id, name, target_amount, currency, due_on, icon, linked_account_id, achieved_at,
                               archived_at, created_by, created_at
                        from goals
                        where workspace_id = :workspaceId
                        order by created_at, id""")
                .param("workspaceId", workspaceId)
                .query((rs, row) -> {
                    Timestamp achievedAt = rs.getTimestamp("achieved_at");
                    Timestamp archivedAt = rs.getTimestamp("archived_at");
                    return new ExportedGoal(
                            rs.getObject("id", UUID.class),
                            rs.getString("name"),
                            money(rs, "target_amount"),
                            rs.getObject("due_on", LocalDate.class),
                            rs.getString("icon"),
                            rs.getObject("linked_account_id", UUID.class),
                            achievedAt == null ? null : achievedAt.toInstant(),
                            archivedAt == null ? null : archivedAt.toInstant(),
                            rs.getObject("created_by", UUID.class),
                            rs.getTimestamp("created_at").toInstant());
                })
                .list();
    }

    @Override
    public List<ExportedContribution> contributions(UUID workspaceId) {
        return jdbc.sql(CONTRIBUTIONS + """
                        where c.workspace_id = :workspaceId
                        order by c.occurred_on, c.id""")
                .param("workspaceId", workspaceId)
                .query(PlanningJdbcExport::contribution)
                .list();
    }

    @Override
    public List<ExportedContribution> contributionsBy(UUID workspaceId, UUID membershipId) {
        return jdbc.sql(CONTRIBUTIONS + """
                        where c.workspace_id = :workspaceId and c.member_id = :membershipId
                        order by c.occurred_on, c.id""")
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .query(PlanningJdbcExport::contribution)
                .list();
    }

    @Override
    public ExportedReserveSettings reserveSettings(UUID workspaceId) {
        return jdbc.sql("select reserve_target_months, manual_monthly_cost from workspaces where id = :workspaceId")
                .param("workspaceId", workspaceId)
                .query((rs, row) -> new ExportedReserveSettings(
                        rs.getInt("reserve_target_months"), rs.getBigDecimal("manual_monthly_cost")))
                .single();
    }

    private static ExportedContribution contribution(ResultSet rs, int row) throws SQLException {
        return new ExportedContribution(
                rs.getObject("id", UUID.class),
                rs.getObject("goal_id", UUID.class),
                rs.getString("goal_name"),
                money(rs, "amount"),
                rs.getObject("occurred_on", LocalDate.class),
                rs.getObject("member_id", UUID.class),
                rs.getTimestamp("created_at").toInstant());
    }

    private static Money money(ResultSet rs, String column) throws SQLException {
        return Money.of(rs.getBigDecimal(column), Currency.getInstance(rs.getString("currency")));
    }

    private static ExportedRecurringRule rule(ResultSet rs, int row) throws SQLException {
        String paymentMethod = rs.getString("payment_method");
        Timestamp archivedAt = rs.getTimestamp("archived_at");
        return new ExportedRecurringRule(
                rs.getObject("id", UUID.class),
                rs.getString("kind"),
                Money.of(rs.getBigDecimal("amount"), Currency.getInstance(rs.getString("currency"))),
                rs.getObject("account_id", UUID.class),
                rs.getObject("to_account_id", UUID.class),
                rs.getObject("category_id", UUID.class),
                paymentMethod == null ? null : PaymentMethod.valueOf(paymentMethod),
                rs.getString("description"),
                rs.getString("frequency"),
                rs.getInt("interval"),
                rs.getObject("start_on", LocalDate.class),
                rs.getObject("end_on", LocalDate.class),
                rs.getObject("next_due_on", LocalDate.class),
                rs.getObject("created_by", UUID.class),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                archivedAt == null ? null : archivedAt.toInstant());
    }
}
