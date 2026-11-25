package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.ExportedAccount;
import br.com.liberdadefamilia.ledger.ExportedCategory;
import br.com.liberdadefamilia.ledger.ExportedTag;
import br.com.liberdadefamilia.ledger.ExportedTransaction;
import br.com.liberdadefamilia.ledger.LedgerExport;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.PaymentMethod;
import br.com.liberdadefamilia.workspace.Role;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads the ledger's tables for the data export. */
@Component
class LedgerJdbcExport implements LedgerExport {

    private static final String TRANSACTIONS = """
            select t.id, t.kind, t.amount, t.currency, t.occurred_on, a.name as account_name,
                   ta.name as to_account_name, c.name as category_name, t.description, t.member_id, t.created_at,
                   t.status, t.payment_method, t.scheduled_on, t.recurring_rule_id, t.import_key, t.venture_id,
                   array(select g.name from transaction_tags tt
                         join tags g on g.workspace_id = tt.workspace_id and g.id = tt.tag_id
                         where tt.workspace_id = t.workspace_id and tt.transaction_id = t.id
                         order by g.name, g.id) as tag_names
            from transactions t
            join accounts a on a.workspace_id = t.workspace_id and a.id = t.account_id
            left join accounts ta on ta.workspace_id = t.workspace_id and ta.id = t.to_account_id
            left join categories c on c.workspace_id = t.workspace_id and c.id = t.category_id
            where t.workspace_id = :workspaceId and t.deleted_at is null""";

    private static final String TAGS = """
            select id, name, created_by, created_at, archived_at
            from tags
            where workspace_id = :workspaceId""";

    private static final String TAGS_ORDER = " order by name, id";

    private static final String NEWEST_FIRST = "\n order by t.occurred_on desc, t.created_at desc, t.id desc";

    private final JdbcClient jdbc;
    private final AccountRepository accounts;

    LedgerJdbcExport(JdbcClient jdbc, AccountRepository accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    @Override
    public List<ExportedAccount> accounts(UUID workspaceId) {
        return accounts.allWithBalances(workspaceId, AccountScope.all()).stream()
                .map(a -> new ExportedAccount(
                        a.id(),
                        a.name(),
                        a.type().name(),
                        a.initialBalance(),
                        a.balance(),
                        a.ownerMembershipId(),
                        a.archivedAt(),
                        a.isReserve()))
                .toList();
    }

    @Override
    public List<ExportedCategory> categories(UUID workspaceId) {
        return jdbc.sql("""
                        select id, parent_id, kind, name, archived_at, is_fixed
                        from categories
                        where workspace_id = :workspaceId
                        order by kind, name, id""")
                .param("workspaceId", workspaceId)
                .query((rs, row) -> {
                    Timestamp archivedAt = rs.getTimestamp("archived_at");
                    return new ExportedCategory(
                            rs.getObject("id", UUID.class),
                            rs.getObject("parent_id", UUID.class),
                            rs.getString("kind"),
                            rs.getString("name"),
                            archivedAt == null ? null : archivedAt.toInstant(),
                            rs.getBoolean("is_fixed"));
                })
                .list();
    }

    @Override
    public List<ExportedTag> tags(UUID workspaceId) {
        return jdbc.sql(TAGS + TAGS_ORDER)
                .param("workspaceId", workspaceId)
                .query(LedgerJdbcExport::tag)
                .list();
    }

    @Override
    public List<ExportedTag> tagsCreatedBy(UUID workspaceId, UUID membershipId) {
        return jdbc.sql(TAGS + " and created_by = :membershipId" + TAGS_ORDER)
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .query(LedgerJdbcExport::tag)
                .list();
    }

    @Override
    public List<ExportedTransaction> transactions(UUID workspaceId) {
        return jdbc.sql(TRANSACTIONS + NEWEST_FIRST)
                .param("workspaceId", workspaceId)
                .query(LedgerJdbcExport::transaction)
                .list();
    }

    @Override
    public List<ExportedTransaction> transactionsOf(UUID workspaceId, UUID membershipId, Role role) {
        AccountScope scope = accounts.scopeOf(workspaceId, membershipId, role);
        return jdbc.sql(TRANSACTIONS + " and (t.member_id = :membershipId or t.created_by = :membershipId) and "
                        + AccountScope.sqlTouches("t.account_id", "t.to_account_id") + NEWEST_FIRST)
                .param("scopeIds", scope.sqlArray())
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .query(LedgerJdbcExport::transaction)
                .list();
    }

    private static ExportedTag tag(ResultSet rs, int row) throws SQLException {
        Timestamp archivedAt = rs.getTimestamp("archived_at");
        return new ExportedTag(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getObject("created_by", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                archivedAt == null ? null : archivedAt.toInstant());
    }

    private static ExportedTransaction transaction(ResultSet rs, int row) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        String paymentMethod = rs.getString("payment_method");
        return new ExportedTransaction(
                rs.getObject("id", UUID.class),
                rs.getString("kind"),
                Money.of(rs.getBigDecimal("amount"), currency),
                rs.getObject("occurred_on", LocalDate.class),
                rs.getString("account_name"),
                rs.getString("to_account_name"),
                rs.getString("category_name"),
                rs.getString("description"),
                rs.getObject("member_id", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("status"),
                paymentMethod == null ? null : PaymentMethod.valueOf(paymentMethod),
                List.of((String[]) rs.getArray("tag_names").getArray()),
                rs.getObject("scheduled_on", LocalDate.class),
                rs.getObject("recurring_rule_id", UUID.class),
                rs.getString("import_key"),
                rs.getObject("venture_id", UUID.class));
    }
}
