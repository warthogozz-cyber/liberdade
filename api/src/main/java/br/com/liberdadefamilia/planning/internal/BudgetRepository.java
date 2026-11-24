package br.com.liberdadefamilia.planning.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The family's monthly limits. Every query is for one family; the month is its first day. */
@Component
class BudgetRepository {

    /** A stored limit. */
    record Row(UUID categoryId, BigDecimal limitAmount, String currency, long version) {}

    private final JdbcClient jdbc;

    BudgetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The month's limits by category name. */
    List<Row> list(UUID workspaceId, LocalDate month) {
        return jdbc.sql("""
                        select b.category_id, b.limit_amount, b.currency, b.version
                        from budgets b
                        join categories c on c.workspace_id = b.workspace_id and c.id = b.category_id
                        where b.workspace_id = :workspaceId and b.month = :month
                        order by c.name, c.id""")
                .param("workspaceId", workspaceId)
                .param("month", month)
                .query((rs, n) -> new Row(
                        rs.getObject("category_id", UUID.class),
                        rs.getBigDecimal("limit_amount"),
                        rs.getString("currency"),
                        rs.getLong("version")))
                .list();
    }

    Optional<Row> find(UUID workspaceId, LocalDate month, UUID categoryId) {
        return jdbc.sql("""
                        select category_id, limit_amount, currency, version
                        from budgets
                        where workspace_id = :workspaceId and month = :month and category_id = :categoryId""")
                .param("workspaceId", workspaceId)
                .param("month", month)
                .param("categoryId", categoryId)
                .query((rs, n) -> new Row(
                        rs.getObject("category_id", UUID.class),
                        rs.getBigDecimal("limit_amount"),
                        rs.getString("currency"),
                        rs.getLong("version")))
                .optional();
    }

    /** Creates the limit unless the category already has one that month; false when it had. */
    boolean insertIfAbsent(UUID workspaceId, LocalDate month, UUID categoryId, BigDecimal amount, String currency) {
        return jdbc.sql("""
                        insert into budgets (workspace_id, category_id, month, limit_amount, currency)
                        values (:workspaceId, :categoryId, :month, :amount, :currency)
                        on conflict (workspace_id, category_id, month) do nothing""")
                        .param("workspaceId", workspaceId)
                        .param("categoryId", categoryId)
                        .param("month", month)
                        .param("amount", amount)
                        .param("currency", currency)
                        .update()
                > 0;
    }

    /** Changes the limit when its version is the given one; false when it is not, or there is no limit. */
    boolean update(
            UUID workspaceId, LocalDate month, UUID categoryId, BigDecimal amount, String currency, long version) {
        return jdbc.sql("""
                        update budgets
                        set limit_amount = :amount, currency = :currency, version = version + 1, updated_at = now()
                        where workspace_id = :workspaceId and month = :month and category_id = :categoryId
                          and version = :version""")
                        .param("workspaceId", workspaceId)
                        .param("month", month)
                        .param("categoryId", categoryId)
                        .param("amount", amount)
                        .param("currency", currency)
                        .param("version", version)
                        .update()
                > 0;
    }

    /** Removes the limit. Whether there was one to remove. */
    boolean delete(UUID workspaceId, LocalDate month, UUID categoryId) {
        return jdbc.sql("""
                        delete from budgets
                        where workspace_id = :workspaceId and month = :month and category_id = :categoryId""")
                        .param("workspaceId", workspaceId)
                        .param("month", month)
                        .param("categoryId", categoryId)
                        .update()
                > 0;
    }

    /** Copies the limits of {@code from} to {@code to}, for the categories that are not archived and have none there. */
    int copy(UUID workspaceId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                insert into budgets (workspace_id, category_id, month, limit_amount, currency)
                select b.workspace_id, b.category_id, :to, b.limit_amount, b.currency
                from budgets b
                join categories c on c.workspace_id = b.workspace_id and c.id = b.category_id
                                 and c.archived_at is null
                where b.workspace_id = :workspaceId and b.month = :from
                  and not exists (select 1 from budgets t
                                  where t.workspace_id = b.workspace_id and t.category_id = b.category_id
                                    and t.month = :to)
                on conflict (workspace_id, category_id, month) do nothing""")
                .param("workspaceId", workspaceId)
                .param("from", from)
                .param("to", to)
                .update();
    }
}
