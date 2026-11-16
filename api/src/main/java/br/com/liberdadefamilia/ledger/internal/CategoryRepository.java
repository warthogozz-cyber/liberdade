package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.domain.TransactionKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
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

    /** What a new transaction needs to know about a category: its kind, and whether it is archived. */
    record CategoryChoice(TransactionKind kind, boolean archived) {}

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
     * The categories of one kind. The active ones come first, most used first: by the number of transactions on or
     * after {@code usedSince} (D31), then by their default order, then by name. The archived ones follow, by name.
     * Deleted transactions do not count.
     */
    List<CategoryView> allByUse(UUID workspaceId, TransactionKind kind, LocalDate usedSince) {
        return jdbc.sql("""
                        select c.id, c.name, c.kind, c.icon, c.color, c.parent_id, c.archived_at
                        from categories c
                        left join transactions t
                               on t.workspace_id = c.workspace_id
                              and t.category_id = c.id
                              and t.deleted_at is null
                              and t.occurred_on >= :usedSince
                        where c.workspace_id = :workspaceId
                          and c.kind = :kind
                        group by c.id
                        order by (c.archived_at is not null),
                                 case when c.archived_at is null then count(t.id) else 0 end desc,
                                 case when c.archived_at is null then c.sort_order else 0 end,
                                 c.name, c.id""")
                .param("workspaceId", workspaceId)
                .param("kind", kind.name())
                .param("usedSince", usedSince)
                .query(CategoryRepository::view)
                .list();
    }

    Optional<CategoryView> find(UUID workspaceId, UUID categoryId) {
        return jdbc.sql("""
                        select id, name, kind, icon, color, parent_id, archived_at
                        from categories
                        where workspace_id = :workspaceId
                          and id = :categoryId""")
                .param("workspaceId", workspaceId)
                .param("categoryId", categoryId)
                .query(CategoryRepository::view)
                .optional();
    }

    /** The kind and archived state of a category of this family. */
    Optional<CategoryChoice> choice(UUID workspaceId, UUID categoryId) {
        return jdbc.sql("""
                        select kind, archived_at is not null as archived
                        from categories
                        where workspace_id = :workspaceId
                          and id = :categoryId""")
                .param("workspaceId", workspaceId)
                .param("categoryId", categoryId)
                .query((rs, row) ->
                        new CategoryChoice(TransactionKind.valueOf(rs.getString("kind")), rs.getBoolean("archived")))
                .optional();
    }

    /** Adds a category after its siblings; the caller has checked the parent. */
    UUID insert(
            UUID workspaceId,
            String name,
            TransactionKind kind,
            @Nullable String icon,
            @Nullable CategoryColor color,
            @Nullable UUID parentId) {
        return jdbc.sql("""
                        insert into categories (workspace_id, parent_id, kind, name, icon, color, sort_order)
                        values (:workspaceId, cast(:parentId as uuid), :kind, :name, :icon, :color,
                                (select coalesce(max(s.sort_order), 0) + 1
                                 from categories s
                                 where s.workspace_id = :workspaceId
                                   and s.kind = :kind
                                   and s.parent_id is not distinct from cast(:parentId as uuid)))
                        returning id""")
                .param("workspaceId", workspaceId)
                .param("parentId", parentId)
                .param("kind", kind.name())
                .param("name", name)
                .param("icon", icon)
                .param("color", color == null ? null : color.name())
                .query(UUID.class)
                .single();
    }

    /** Changes what is not null. Whether the category exists in this family. */
    boolean update(
            UUID workspaceId,
            UUID categoryId,
            @Nullable String name,
            @Nullable String icon,
            @Nullable CategoryColor color) {
        return jdbc.sql("""
                        update categories
                        set name = coalesce(cast(:name as text), name),
                            icon = coalesce(cast(:icon as text), icon),
                            color = coalesce(cast(:color as text), color),
                            version = version + 1,
                            updated_at = now()
                        where workspace_id = :workspaceId
                          and id = :categoryId""")
                        .param("workspaceId", workspaceId)
                        .param("categoryId", categoryId)
                        .param("name", name)
                        .param("icon", icon)
                        .param("color", color == null ? null : color.name())
                        .update()
                == 1;
    }

    /** Archives the category and its active subcategories with one timestamp; false when none was active. */
    boolean archiveWithChildren(UUID workspaceId, UUID categoryId, Instant now) {
        return jdbc.sql("""
                        update categories
                        set archived_at = :now, version = version + 1, updated_at = now()
                        where workspace_id = :workspaceId
                          and (id = :categoryId or parent_id = :categoryId)
                          and archived_at is null""")
                        .param("workspaceId", workspaceId)
                        .param("categoryId", categoryId)
                        .param("now", Timestamp.from(now))
                        .update()
                > 0;
    }

    /**
     * Brings the category back, and the subcategories archived with it (the ones with its own timestamp). False when
     * it was already active or is not in this family.
     */
    boolean unarchiveWithChildren(UUID workspaceId, UUID categoryId) {
        return jdbc.sql("""
                        update categories
                        set archived_at = null, version = version + 1, updated_at = now()
                        where workspace_id = :workspaceId
                          and archived_at is not null
                          and (id = :categoryId
                               or (parent_id = :categoryId
                                   and archived_at = (select p.archived_at
                                                      from categories p
                                                      where p.workspace_id = :workspaceId
                                                        and p.id = :categoryId)))""")
                        .param("workspaceId", workspaceId)
                        .param("categoryId", categoryId)
                        .update()
                > 0;
    }

    /**
     * How many top-level categories of the kind are active. It locks them, so that two archivals at once cannot both
     * see another active one and leave the family with none.
     */
    int countActiveTopLevel(UUID workspaceId, TransactionKind kind) {
        return jdbc.sql("""
                        select count(*)
                        from (select 1
                              from categories
                              where workspace_id = :workspaceId
                                and kind = :kind
                                and parent_id is null
                                and archived_at is null
                              for update) active""")
                .param("workspaceId", workspaceId)
                .param("kind", kind.name())
                .query(Integer.class)
                .single();
    }

    private static CategoryView view(ResultSet rs, int row) throws SQLException {
        String color = rs.getString("color");
        Timestamp archivedAt = rs.getTimestamp("archived_at");
        return new CategoryView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                TransactionKind.valueOf(rs.getString("kind")),
                rs.getString("icon"),
                color == null ? null : CategoryColor.valueOf(color),
                rs.getObject("parent_id", UUID.class),
                archivedAt == null ? null : archivedAt.toInstant());
    }
}
