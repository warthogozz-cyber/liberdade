package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.Money;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The family's transactions. Deleted ones are left out of every read. */
@Component
class TransactionRepository {

    private static final String COLUMNS = """
            id, kind, amount, currency, occurred_on, account_id, category_id, description, member_id, created_at""";

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
                             account_id, category_id, description, member_id, created_by)
                        values (:id, :workspaceId, :kind, :amount, :currency, :occurredOn,
                                :accountId, :categoryId, :description, :membershipId, :membershipId)
                        on conflict (id) do nothing
                        returning\s""" + COLUMNS)
                .param("id", request.id())
                .param("workspaceId", workspaceId)
                .param("kind", request.kind().name())
                .param("amount", request.amount().amount())
                .param("currency", request.amount().currency().getCurrencyCode())
                .param("occurredOn", request.occurredOn())
                .param("accountId", request.accountId())
                .param("categoryId", request.categoryId())
                .param("description", request.description())
                .param("membershipId", membershipId)
                .query(TransactionRepository::view)
                .optional();
    }

    /** A transaction of this family, deleted or not: an id stays taken after deletion. */
    Optional<TransactionView> find(UUID workspaceId, UUID id) {
        return jdbc.sql("select " + COLUMNS + " from transactions where workspace_id = :workspaceId and id = :id")
                .param("workspaceId", workspaceId)
                .param("id", id)
                .query(TransactionRepository::view)
                .optional();
    }

    /** The transactions on or after {@code from} and before {@code until}, newest first (D20). */
    List<TransactionView> between(UUID workspaceId, LocalDate from, LocalDate until) {
        return jdbc.sql("select " + COLUMNS + """
                         from transactions
                        where workspace_id = :workspaceId
                          and occurred_on >= :from
                          and occurred_on < :until
                          and deleted_at is null
                        order by occurred_on desc, created_at desc, id desc""")
                .param("workspaceId", workspaceId)
                .param("from", from)
                .param("until", until)
                .query(TransactionRepository::view)
                .list();
    }

    /** The sum of the expenses in the given currency on or after {@code from} and before {@code until}. */
    Money expenses(UUID workspaceId, Currency currency, LocalDate from, LocalDate until) {
        return Money.of(
                jdbc.sql("""
                                select coalesce(sum(amount), 0)
                                from transactions
                                where workspace_id = :workspaceId
                                  and kind = 'EXPENSE'
                                  and currency = :currency
                                  and occurred_on >= :from
                                  and occurred_on < :until
                                  and deleted_at is null""")
                        .param("workspaceId", workspaceId)
                        .param("currency", currency.getCurrencyCode())
                        .param("from", from)
                        .param("until", until)
                        .query(BigDecimal.class)
                        .single(),
                currency);
    }

    private static TransactionView view(ResultSet rs, int row) throws SQLException {
        return new TransactionView(
                rs.getObject("id", UUID.class),
                TransactionKind.valueOf(rs.getString("kind")),
                Money.of(rs.getBigDecimal("amount"), Currency.getInstance(rs.getString("currency"))),
                rs.getObject("occurred_on", LocalDate.class),
                rs.getObject("account_id", UUID.class),
                rs.getObject("category_id", UUID.class),
                rs.getString("description"),
                rs.getObject("member_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
