package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.TransactionKind;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Statement import keys: the ones on transactions (deleted ones included) and the tombstones of purged ones. */
@Repository
class ImportRepository {

    private final JdbcClient jdbc;

    ImportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A confirmed transaction that has a category, as the hints read it. */
    record CategorizedRow(TransactionKind kind, @Nullable String description, UUID categoryId) {}

    /** Whether a transaction (deleted or not) or a tombstone of the family already holds the key. */
    boolean keyTaken(UUID workspaceId, String importKey) {
        return jdbc.sql("""
                        select exists (select 1 from transactions where workspace_id = :ws and import_key = :key)
                            or exists (select 1 from import_tombstones where workspace_id = :ws and import_key = :key)""")
                .param("ws", workspaceId)
                .param("key", importKey)
                .query(Boolean.class)
                .single();
    }

    /**
     * The confirmed income and expenses on or after {@code since}, not deleted, in accounts of the scope and in a
     * category that is not archived; the newest first.
     */
    List<CategorizedRow> recentCategorized(UUID workspaceId, AccountScope scope, LocalDate since) {
        return jdbc.sql("""
                        select t.kind, t.description, t.category_id
                        from transactions t
                        join categories c on c.workspace_id = t.workspace_id and c.id = t.category_id
                        where t.workspace_id = :ws
                          and t.status = 'CONFIRMED'
                          and t.deleted_at is null
                          and t.kind in ('INCOME', 'EXPENSE')
                          and t.occurred_on >= :since
                          and c.archived_at is null
                          and """ + AccountScope.sqlIncludes("t.account_id") + """

                        order by t.occurred_on desc, t.created_at desc, t.id desc""")
                .param("ws", workspaceId)
                .param("since", since)
                .param("scopeIds", scope.sqlArray())
                .query((rs, n) -> new CategorizedRow(
                        TransactionKind.valueOf(rs.getString("kind")),
                        rs.getString("description"),
                        rs.getObject("category_id", UUID.class)))
                .list();
    }

    /** Removes the tombstones purged before the cutoff and answers how many went. */
    int purgeTombstonesBefore(Instant cutoff) {
        return jdbc.sql("delete from import_tombstones where purged_at < :cutoff")
                .param("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC))
                .update();
    }
}
