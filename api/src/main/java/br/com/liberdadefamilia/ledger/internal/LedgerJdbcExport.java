package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.ExportedAccount;
import br.com.liberdadefamilia.ledger.ExportedCategory;
import br.com.liberdadefamilia.ledger.ExportedTransaction;
import br.com.liberdadefamilia.ledger.LedgerExport;
import br.com.liberdadefamilia.shared.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
                   ta.name as to_account_name, c.name as category_name, t.description, t.member_id, t.created_at
            from transactions t
            join accounts a on a.workspace_id = t.workspace_id and a.id = t.account_id
            left join accounts ta on ta.workspace_id = t.workspace_id and ta.id = t.to_account_id
            left join categories c on c.workspace_id = t.workspace_id and c.id = t.category_id
            where t.workspace_id = :workspaceId and t.deleted_at is null""";

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
                        a.archivedAt()))
                .toList();
    }

    @Override
    public List<ExportedCategory> categories(UUID workspaceId) {
        return jdbc.sql("""
                        select id, parent_id, kind, name, archived_at
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
                            archivedAt == null ? null : archivedAt.toInstant());
                })
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
    public List<ExportedTransaction> transactionsOf(UUID workspaceId, UUID membershipId) {
        return jdbc.sql(TRANSACTIONS + " and (t.member_id = :membershipId or t.created_by = :membershipId)"
                        + NEWEST_FIRST)
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .query(LedgerJdbcExport::transaction)
                .list();
    }

    private static ExportedTransaction transaction(ResultSet rs, int row) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        return new ExportedTransaction(
                rs.getObject("id", UUID.class),
                rs.getString("kind"),
                Money.of(rs.getBigDecimal("amount"), currency),
                rs.getObject("occurred_on", java.time.LocalDate.class),
                rs.getString("account_name"),
                rs.getString("to_account_name"),
                rs.getString("category_name"),
                rs.getString("description"),
                rs.getObject("member_id", UUID.class),
                rs.getTimestamp("created_at").toInstant());
    }
}
