package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.ProductsMerged;
import br.com.liberdadefamilia.markets.internal.MarketViews.ProductOverview;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Joins two products into one (D4): everything that pointed at the second points at the first, then the second goes
 * away. All in one transaction, so a refusal leaves nothing changed.
 */
@Service
class ProductMergeService {

    private final JdbcClient jdbc;
    private final ProductsService products;
    private final ApplicationEventPublisher events;

    ProductMergeService(JdbcClient jdbc, ProductsService products, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.products = products;
        this.events = events;
    }

    /**
     * Folds {@code removeId} into {@code keepId} and returns the product that stayed.
     *
     * @throws ProductRuleException when both ids are the same, or both products have a GTIN and they differ
     * @throws ProductNotFoundException when the family has no such product
     */
    @Transactional
    ProductOverview merge(UUID workspaceId, UUID actorUserId, UUID keepId, UUID removeId) {
        if (keepId.equals(removeId)) {
            throw ProductRuleException.mergeSame();
        }
        // Lock both rows, in id order, so two joins that touch the same product wait for each other.
        List<Locked> locked = jdbc.sql("""
                        select id, gtin from products
                        where workspace_id = :ws and id in (:keep, :remove)
                        order by id for update""")
                .param("ws", workspaceId)
                .param("keep", keepId)
                .param("remove", removeId)
                .query((rs, n) -> new Locked(rs.getObject("id", UUID.class), rs.getString("gtin")))
                .list();
        if (locked.size() != 2) {
            throw new ProductNotFoundException();
        }
        String keepGtin = gtinOf(locked, keepId);
        String removeGtin = gtinOf(locked, removeId);
        if (keepGtin != null && removeGtin != null && !keepGtin.equals(removeGtin)) {
            throw ProductRuleException.mergeGtinConflict();
        }

        movePrices(workspaceId, keepId, removeId);
        moveAliases(workspaceId, keepId, removeId);
        moveListItem(workspaceId, keepId, removeId);
        dropDismissals(workspaceId, removeId);
        // The second goes before the first takes its GTIN: products_gtin_once allows one product per code.
        jdbc.sql("delete from products where workspace_id = :ws and id = :id")
                .param("ws", workspaceId)
                .param("id", removeId)
                .update();
        if (keepGtin == null && removeGtin != null) {
            jdbc.sql("update products set gtin = :gtin where workspace_id = :ws and id = :id")
                    .param("gtin", removeGtin)
                    .param("ws", workspaceId)
                    .param("id", keepId)
                    .update();
        }
        events.publishEvent(new ProductsMerged(workspaceId, keepId, removeId, actorUserId));
        return products.one(workspaceId, keepId);
    }

    private void movePrices(UUID ws, UUID keepId, UUID removeId) {
        jdbc.sql("update price_records set product_id = :keep where workspace_id = :ws and product_id = :remove")
                .param("keep", keepId)
                .param("ws", ws)
                .param("remove", removeId)
                .update();
    }

    /** The first's alias wins where both name the same description at the same market. */
    private void moveAliases(UUID ws, UUID keepId, UUID removeId) {
        jdbc.sql("""
                        delete from product_aliases r
                        where r.workspace_id = :ws and r.product_id = :remove
                          and exists (select 1 from product_aliases k
                                      where k.workspace_id = r.workspace_id and k.product_id = :keep
                                        and k.market_id = r.market_id and k.description_norm = r.description_norm)""")
                .param("ws", ws)
                .param("keep", keepId)
                .param("remove", removeId)
                .update();
        jdbc.sql("update product_aliases set product_id = :keep where workspace_id = :ws and product_id = :remove")
                .param("keep", keepId)
                .param("ws", ws)
                .param("remove", removeId)
                .update();
    }

    /** Both listed: one item with the sum, checked only if both were. Only the second listed: the item moves over. */
    private void moveListItem(UUID ws, UUID keepId, UUID removeId) {
        int summed = jdbc.sql("""
                        update shopping_list_items k
                        set quantity = k.quantity + r.quantity,
                            checked_at = case when k.checked_at is not null and r.checked_at is not null
                                              then k.checked_at end
                        from shopping_list_items r
                        where k.workspace_id = :ws and k.product_id = :keep
                          and r.workspace_id = :ws and r.product_id = :remove""")
                .param("ws", ws)
                .param("keep", keepId)
                .param("remove", removeId)
                .update();
        if (summed > 0) {
            jdbc.sql("delete from shopping_list_items where workspace_id = :ws and product_id = :remove")
                    .param("ws", ws)
                    .param("remove", removeId)
                    .update();
            return;
        }
        jdbc.sql("update shopping_list_items set product_id = :keep where workspace_id = :ws and product_id = :remove")
                .param("keep", keepId)
                .param("ws", ws)
                .param("remove", removeId)
                .update();
    }

    private void dropDismissals(UUID ws, UUID removeId) {
        jdbc.sql("""
                        delete from product_join_dismissals
                        where workspace_id = :ws and (product_a = :remove or product_b = :remove)""").param("ws", ws).param("remove", removeId).update();
    }

    private static @Nullable String gtinOf(List<Locked> locked, UUID id) {
        return locked.stream()
                .filter(each -> each.id().equals(id))
                .findFirst()
                .orElseThrow()
                .gtin();
    }

    private record Locked(UUID id, @Nullable String gtin) {}
}
