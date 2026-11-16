package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.domain.CategoryTotal;
import br.com.liberdadefamilia.ledger.domain.Flow;
import br.com.liberdadefamilia.ledger.domain.TransactionKind;
import br.com.liberdadefamilia.shared.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The family's transactions. Deleted ones are left out of every list and sum; they stay findable by id until purged,
 * so that they can be restored.
 */
@Component
class TransactionRepository {

    private static final String COLUMNS = """
            id, kind, amount, currency, occurred_on, account_id, to_account_id, category_id, description, member_id,
            created_at, version""";

    private final JdbcClient jdbc;

    TransactionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts the transaction, recorded by and for the given member, unless its id already exists (in this family or
     * any other). Returns the new row, or empty when the id was taken.
     */
    Optional<TransactionView> insertIfAbsent(UUID workspaceId, UUID membershipId, CreateTransactionRequest request) {
        return jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on,
                             account_id, to_account_id, category_id, description, member_id, created_by)
                        values (:id, :workspaceId, :kind, :amount, :currency, :occurredOn,
                                :accountId, :toAccountId, :categoryId, :description, :membershipId, :membershipId)
                        on conflict (id) do nothing
                        returning\s""" + COLUMNS)
                .param("id", request.id())
                .param("workspaceId", workspaceId)
                .param("kind", request.kind().name())
                .param("amount", request.amount().amount())
                .param("currency", request.amount().currency().getCurrencyCode())
                .param("occurredOn", request.occurredOn())
                .param("accountId", request.accountId())
                .param("toAccountId", request.toAccountId())
                .param("categoryId", request.categoryId())
                .param("description", request.description())
                .param("membershipId", membershipId)
                .query(TransactionRepository::view)
                .optional();
    }

    /** A transaction of this family, deleted or not: an id stays taken after deletion. */
    Optional<StoredTransaction> find(UUID workspaceId, UUID id) {
        return jdbc.sql("select " + COLUMNS + """
                        , deleted_at is not null as deleted
                         from transactions
                        where workspace_id = :workspaceId
                          and id = :id""")
                .param("workspaceId", workspaceId)
                .param("id", id)
                .query((rs, row) -> new StoredTransaction(view(rs, row), rs.getBoolean("deleted")))
                .optional();
    }

    /**
     * Replaces the transaction's data, if it is still at the given version and not deleted, and moves it to the next
     * version. Empty when the version moved on or the transaction was deleted (or never existed).
     */
    Optional<TransactionView> update(UUID workspaceId, UUID id, UpdateTransactionRequest request) {
        return jdbc.sql("""
                        update transactions
                        set kind = :kind,
                            amount = :amount,
                            currency = :currency,
                            occurred_on = :occurredOn,
                            account_id = :accountId,
                            to_account_id = :toAccountId,
                            category_id = :categoryId,
                            description = :description,
                            version = version + 1,
                            updated_at = now()
                        where workspace_id = :workspaceId
                          and id = :id
                          and version = :version
                          and deleted_at is null
                        returning\s""" + COLUMNS)
                .param("workspaceId", workspaceId)
                .param("id", id)
                .param("version", request.version())
                .param("kind", request.kind().name())
                .param("amount", request.amount().amount())
                .param("currency", request.amount().currency().getCurrencyCode())
                .param("occurredOn", request.occurredOn())
                .param("accountId", request.accountId())
                .param("toAccountId", request.toAccountId())
                .param("categoryId", request.categoryId())
                .param("description", request.description())
                .query(TransactionRepository::view)
                .optional();
    }

    /** Marks an active transaction as deleted at {@code now}. Whether one was; false when absent or already deleted. */
    boolean softDelete(UUID workspaceId, UUID id, Instant now) {
        return jdbc.sql("""
                        update transactions
                        set deleted_at = :now,
                            version = version + 1,
                            updated_at = now()
                        where workspace_id = :workspaceId
                          and id = :id
                          and deleted_at is null""")
                        .param("workspaceId", workspaceId)
                        .param("id", id)
                        .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                        .update()
                == 1;
    }

    /** Brings a deleted transaction back. Empty when it is absent or not deleted. */
    Optional<TransactionView> restore(UUID workspaceId, UUID id) {
        return jdbc.sql("""
                        update transactions
                        set deleted_at = null,
                            version = version + 1,
                            updated_at = now()
                        where workspace_id = :workspaceId
                          and id = :id
                          and deleted_at is not null
                        returning\s""" + COLUMNS)
                .param("workspaceId", workspaceId)
                .param("id", id)
                .query(TransactionRepository::view)
                .optional();
    }

    /**
     * The transactions on or after {@code from} and before {@code until}, newest first (D20). With a category, only
     * that category and its subcategories; with an account, the transactions leaving or entering it.
     */
    List<TransactionView> between(
            UUID workspaceId, LocalDate from, LocalDate until, @Nullable UUID categoryId, @Nullable UUID accountId) {
        return jdbc.sql("select " + COLUMNS + """
                         from transactions
                        where workspace_id = :workspaceId
                          and occurred_on >= :from
                          and occurred_on < :until
                          and deleted_at is null
                          and (cast(:categoryId as uuid) is null
                               or category_id in (select c.id
                                                  from categories c
                                                  where c.workspace_id = :workspaceId
                                                    and (c.id = cast(:categoryId as uuid)
                                                         or c.parent_id = cast(:categoryId as uuid))))
                          and (cast(:accountId as uuid) is null
                               or account_id = cast(:accountId as uuid)
                               or to_account_id = cast(:accountId as uuid))
                        order by occurred_on desc, created_at desc, id desc""")
                .param("workspaceId", workspaceId)
                .param("from", from)
                .param("until", until)
                .param("categoryId", categoryId)
                .param("accountId", accountId)
                .query(TransactionRepository::view)
                .list();
    }

    /**
     * What moved up to {@code monthEnd}, summed by day, kind, accounts and currency. What happened before the month
     * collapses into the day before {@code monthStart}, so that years of history come back as a few flows.
     */
    List<Flow> flowsUntil(UUID workspaceId, LocalDate monthStart, LocalDate monthEnd) {
        return jdbc.sql("""
                        select greatest(occurred_on, cast(:monthStart as date) - 1) as day,
                               kind, account_id, to_account_id, currency, sum(amount) as amount
                        from transactions
                        where workspace_id = :workspaceId
                          and occurred_on <= :monthEnd
                          and deleted_at is null
                        group by 1, kind, account_id, to_account_id, currency""")
                .param("workspaceId", workspaceId)
                .param("monthStart", monthStart)
                .param("monthEnd", monthEnd)
                .query((rs, row) -> new Flow(
                        rs.getObject("day", LocalDate.class),
                        TransactionKind.valueOf(rs.getString("kind")),
                        rs.getObject("account_id", UUID.class),
                        rs.getObject("to_account_id", UUID.class),
                        Money.of(rs.getBigDecimal("amount"), Currency.getInstance(rs.getString("currency")))))
                .list();
    }

    /**
     * The expenses of the month and of the month before, by top-level category: a subcategory counts for its parent
     * (D9). One line for each category and currency.
     */
    List<CategoryTotal> expensesByTopCategory(UUID workspaceId, YearMonth month) {
        return jdbc.sql("""
                        select coalesce(c.parent_id, c.id) as category_id, t.currency,
                               coalesce(sum(t.amount) filter (where t.occurred_on >= :monthStart), 0) as current,
                               coalesce(sum(t.amount) filter (where t.occurred_on < :monthStart), 0) as previous
                        from transactions t
                        join categories c
                          on c.workspace_id = t.workspace_id
                         and c.id = t.category_id
                        where t.workspace_id = :workspaceId
                          and t.kind = 'EXPENSE'
                          and t.deleted_at is null
                          and t.occurred_on >= :previousStart
                          and t.occurred_on < :nextStart
                        group by coalesce(c.parent_id, c.id), t.currency""")
                .param("workspaceId", workspaceId)
                .param("monthStart", month.atDay(1))
                .param("previousStart", month.minusMonths(1).atDay(1))
                .param("nextStart", month.plusMonths(1).atDay(1))
                .query((rs, row) -> {
                    Currency currency = Currency.getInstance(rs.getString("currency"));
                    return new CategoryTotal(
                            rs.getObject("category_id", UUID.class),
                            Money.of(rs.getBigDecimal("current"), currency),
                            Money.of(rs.getBigDecimal("previous"), currency));
                })
                .list();
    }

    /**
     * Removes for good the transactions deleted before the cutoff, in every family, and returns how many. The one method
     * here without a {@code workspaceId}: the nightly purge sweeps all families at once and serves no request, so there
     * is no family to scope it to, and it only removes rows that their own families already deleted.
     */
    int purgeDeletedBefore(Instant cutoff) {
        return jdbc.sql("""
                        delete from transactions
                        where deleted_at is not null
                          and deleted_at < :cutoff""")
                .param("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC))
                .update();
    }

    private static TransactionView view(ResultSet rs, int row) throws SQLException {
        return new TransactionView(
                rs.getObject("id", UUID.class),
                TransactionKind.valueOf(rs.getString("kind")),
                Money.of(rs.getBigDecimal("amount"), Currency.getInstance(rs.getString("currency"))),
                rs.getObject("occurred_on", LocalDate.class),
                rs.getObject("account_id", UUID.class),
                rs.getObject("to_account_id", UUID.class),
                rs.getObject("category_id", UUID.class),
                rs.getString("description"),
                rs.getObject("member_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getLong("version"));
    }

    /** A transaction as stored, with whether it is deleted (and so restorable, not editable). */
    record StoredTransaction(TransactionView view, boolean deleted) {}
}
