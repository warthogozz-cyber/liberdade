package br.com.liberdadefamilia.markets.internal;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The family's {@code shopping_list_items}. Every query filters by the family. */
@Component
class ShoppingListRepository {

    private static final String SELECT = """
            select i.id, i.product_id, p.name, p.unit, i.quantity, i.checked_at is not null as checked, i.position
            from shopping_list_items i
            join products p on p.workspace_id = i.workspace_id and p.id = i.product_id""";

    private final JdbcClient jdbc;

    ShoppingListRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** An item with the product's current name and unit. */
    record ItemRow(
            UUID id, UUID productId, String name, String unit, BigDecimal quantity, boolean checked, int position) {}

    /** What an add did: the item, and whether this call created it. */
    record Added(UUID id, boolean created) {}

    /**
     * Serializes the adds of one family until the transaction ends, so the limit, the end position and a product made
     * from a name cannot race: the table has no uniqueness on names to catch two threads making the same one.
     */
    void lockAdds(UUID workspaceId) {
        jdbc.sql("select 1 from (select pg_advisory_xact_lock(hashtextextended(:key, 0))) locked")
                .param("key", "shopping-list:" + workspaceId)
                .query(Integer.class)
                .single();
    }

    int count(UUID workspaceId) {
        return jdbc.sql("select count(*) from shopping_list_items where workspace_id = :ws")
                .param("ws", workspaceId)
                .query(Integer.class)
                .single();
    }

    boolean contains(UUID workspaceId, UUID productId) {
        return jdbc.sql("""
                        select exists (
                            select 1 from shopping_list_items where workspace_id = :ws and product_id = :product)""")
                .param("ws", workspaceId)
                .param("product", productId)
                .query(Boolean.class)
                .single();
    }

    /** Adds at the end, or adds the quantity to the item the product already has and unticks it. */
    Added add(UUID workspaceId, UUID productId, BigDecimal quantity, UUID userId) {
        return jdbc.sql("""
                        insert into shopping_list_items (workspace_id, product_id, quantity, position, created_by)
                        values (:ws, :product, :quantity,
                                (select coalesce(max(position) + 1, 0)
                                 from shopping_list_items where workspace_id = :ws), :user)
                        on conflict (workspace_id, product_id) do update
                        set quantity = shopping_list_items.quantity + excluded.quantity, checked_at = null
                        returning id, (xmax = 0) as created""")
                .param("ws", workspaceId)
                .param("product", productId)
                .param("quantity", quantity)
                .param("user", userId)
                .query((rs, n) -> new Added(rs.getObject("id", UUID.class), rs.getBoolean("created")))
                .single();
    }

    List<ItemRow> items(UUID workspaceId) {
        return jdbc.sql(SELECT + " where i.workspace_id = :ws order by i.position, i.id")
                .param("ws", workspaceId)
                .query(ShoppingListRepository::row)
                .list();
    }

    Optional<ItemRow> find(UUID workspaceId, UUID id) {
        return jdbc.sql(SELECT + " where i.workspace_id = :ws and i.id = :id")
                .param("ws", workspaceId)
                .param("id", id)
                .query(ShoppingListRepository::row)
                .optional();
    }

    void setQuantity(UUID workspaceId, UUID id, BigDecimal quantity) {
        jdbc.sql("update shopping_list_items set quantity = :quantity where workspace_id = :ws and id = :id")
                .param("ws", workspaceId)
                .param("id", id)
                .param("quantity", quantity)
                .update();
    }

    void setChecked(UUID workspaceId, UUID id, boolean checked) {
        jdbc.sql("""
                        update shopping_list_items
                        set checked_at = case when :checked then coalesce(checked_at, now()) end
                        where workspace_id = :ws and id = :id""")
                .param("ws", workspaceId)
                .param("id", id)
                .param("checked", checked)
                .update();
    }

    /** The ids of the whole list, locked until the transaction ends, so no add or removal slips in during a reorder. */
    List<UUID> lockIds(UUID workspaceId) {
        return jdbc.sql("select id from shopping_list_items where workspace_id = :ws for update")
                .param("ws", workspaceId)
                .query(UUID.class)
                .list();
    }

    void setPosition(UUID workspaceId, UUID id, int position) {
        jdbc.sql("update shopping_list_items set position = :position where workspace_id = :ws and id = :id")
                .param("ws", workspaceId)
                .param("id", id)
                .param("position", position)
                .update();
    }

    boolean delete(UUID workspaceId, UUID id) {
        return jdbc.sql("delete from shopping_list_items where workspace_id = :ws and id = :id")
                        .param("ws", workspaceId)
                        .param("id", id)
                        .update()
                > 0;
    }

    int deleteChecked(UUID workspaceId) {
        return jdbc.sql("delete from shopping_list_items where workspace_id = :ws and checked_at is not null")
                .param("ws", workspaceId)
                .update();
    }

    private static ItemRow row(ResultSet rs, int n) throws SQLException {
        return new ItemRow(
                rs.getObject("id", UUID.class),
                rs.getObject("product_id", UUID.class),
                rs.getString("name"),
                rs.getString("unit"),
                rs.getBigDecimal("quantity"),
                rs.getBoolean("checked"),
                rs.getInt("position"));
    }
}
