package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.planning.ExportedRecurringRule;
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
