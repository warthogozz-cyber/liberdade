package br.com.liberdadefamilia.ledger.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The family's categories. Their kind is the kind of transaction they take: INCOME or EXPENSE. */
@Component
class CategoryRepository {

    private final JdbcClient jdbc;

    CategoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insertDefault(UUID workspaceId, TransactionKind kind, String name, String icon, int sortOrder) {
        jdbc.sql("""
                        insert into categories (workspace_id, kind, name, icon, is_default, sort_order)
                        values (:workspaceId, :kind, :name, :icon, true, :sortOrder)""")
                .param("workspaceId", workspaceId)
                .param("kind", kind.name())
                .param("name", name)
                .param("icon", icon)
                .param("sortOrder", sortOrder)
                .update();
    }

    /**
     * The categories of one kind that are not archived, most used first: by the number of transactions on or after
     * {@code usedSince} (D31), then by their default order, then by name. Deleted transactions do not count.
     */
    List<Category> unarchivedByUse(UUID workspaceId, TransactionKind kind, LocalDate usedSince) {
        return jdbc.sql("""
                        select c.id, c.name, c.kind, c.icon
                        from categories c
                        left join transactions t
                               on t.workspace_id = c.workspace_id
                              and t.category_id = c.id
                              and t.deleted_at is null
                              and t.occurred_on >= :usedSince
                        where c.workspace_id = :workspaceId
                          and c.kind = :kind
                          and c.archived_at is null
                        group by c.id
                        order by count(t.id) desc, c.sort_order, c.name, c.id""")
                .param("workspaceId", workspaceId)
                .param("kind", kind.name())
                .param("usedSince", usedSince)
                .query((rs, row) -> new Category(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        TransactionKind.valueOf(rs.getString("kind")),
                        rs.getString("icon")))
                .list();
    }

    /** The kind of a category of this family that is not archived. */
    Optional<TransactionKind> unarchivedKind(UUID workspaceId, UUID categoryId) {
        return jdbc.sql("""
                        select kind
                        from categories
                        where workspace_id = :workspaceId
                          and id = :categoryId
                          and archived_at is null""")
                .param("workspaceId", workspaceId)
                .param("categoryId", categoryId)
                .query((rs, row) -> TransactionKind.valueOf(rs.getString("kind")))
                .optional();
    }

    /** A category as {@code GET /categories} lists it. */
    record Category(
            UUID id,
            String name,
            TransactionKind kind,
            @Nullable String icon) {}
}
