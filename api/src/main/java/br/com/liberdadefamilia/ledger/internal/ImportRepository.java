package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.TransactionKind;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
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
     * Confirms the planned transaction with the line: its date, value and key, entered by the member, one version up.
     * Only while it is still planned, not deleted, of the same kind, in the account and in the scope; whether it was.
     * When another import stored the same key in the meantime, the key index refuses the update and the whole batch
     * answers 409: sending it again skips the line by its key. The problem carries no line data.
     */
    boolean confirmPlanned(
            UUID workspaceId, UUID plannedId, UUID accountId, AccountScope scope, UUID memberId, ImportRow row) {
        try {
            return updatePlanned(workspaceId, plannedId, accountId, scope, memberId, row);
        } catch (DuplicateKeyException race) {
            throw new LedgerConflictException(
                    "import.concurrent", "Outra importação do mesmo extrato terminou antes. Tente de novo.");
        }
    }

    private boolean updatePlanned(
            UUID workspaceId, UUID plannedId, UUID accountId, AccountScope scope, UUID memberId, ImportRow row) {
        return jdbc.sql("""
                        update transactions
                        set status = 'CONFIRMED',
                            occurred_on = :occurredOn,
                            amount = :amount,
                            member_id = :memberId,
                            import_key = :importKey,
                            version = version + 1,
                            updated_at = now()
                        where workspace_id = :ws
                          and id = :id
                          and account_id = :accountId
                          and kind = :kind
                          and status = 'PLANNED'
                          and deleted_at is null
                          and """ + AccountScope.sqlIncludes("account_id"))
                        .param("occurredOn", row.occurredOn())
                        .param("amount", row.amount().amount())
                        .param("memberId", memberId)
                        .param("importKey", row.importKey())
                        .param("ws", workspaceId)
                        .param("id", plannedId)
                        .param("accountId", accountId)
                        .param("kind", row.kind().name())
                        .param("scopeIds", scope.sqlArray())
                        .update()
                == 1;
    }

    /**
     * Inserts the line as a confirmed transaction in the account, recorded by and for the member, with no payment
     * method. Nothing happens when the id or the key is already taken (deleted rows included) or the key has a
     * tombstone; whether it was inserted.
     */
    boolean insertImported(UUID workspaceId, UUID accountId, UUID memberId, ImportRow row) {
        return jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on, account_id, category_id,
                             description, member_id, created_by, import_key)
                        select :id, :ws, :kind, :amount, :currency, :occurredOn, :accountId, :categoryId,
                               :description, :memberId, :memberId, :importKey
                        where not exists (
                            select 1 from import_tombstones where workspace_id = :ws and import_key = :importKey)
                        on conflict do nothing
                        returning id""")
                .param("id", row.id())
                .param("ws", workspaceId)
                .param("kind", row.kind().name())
                .param("amount", row.amount().amount())
                .param("currency", row.amount().currency().getCurrencyCode())
                .param("occurredOn", row.occurredOn())
                .param("accountId", accountId)
                .param("categoryId", row.categoryId())
                .param("description", row.description())
                .param("memberId", memberId)
                .param("importKey", row.importKey())
                .query(UUID.class)
                .optional()
                .isPresent();
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
