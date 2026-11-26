package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.ProductUnit;
import br.com.liberdadefamilia.markets.domain.TextNorm;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The family's {@code products} and the aliases that name them at a market. Every query filters by the family. */
@Component
class ProductRepository {

    private final JdbcClient jdbc;

    ProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A product as the family sees it. */
    record ProductRow(
            UUID id, String name, String unit, @Nullable String gtin) {}

    Optional<UUID> byGtin(UUID workspaceId, String gtin) {
        return jdbc.sql("select id from products where workspace_id = :ws and gtin = :gtin")
                .param("ws", workspaceId)
                .param("gtin", gtin)
                .query(UUID.class)
                .optional();
    }

    /** The product this description names at this market ({@code norm} is already {@link TextNorm normalized}). */
    Optional<UUID> byAlias(UUID workspaceId, UUID marketId, String norm) {
        return jdbc.sql("""
                        select product_id from product_aliases
                        where workspace_id = :ws and market_id = :market and description_norm = :norm""")
                .param("ws", workspaceId)
                .param("market", marketId)
                .param("norm", norm)
                .query(UUID.class)
                .optional();
    }

    boolean exists(UUID workspaceId, UUID productId) {
        return jdbc.sql("select exists (select 1 from products where workspace_id = :ws and id = :id)")
                .param("ws", workspaceId)
                .param("id", productId)
                .query(Boolean.class)
                .single();
    }

    UUID create(UUID workspaceId, String name, ProductUnit unit) {
        return jdbc.sql("""
                        insert into products (workspace_id, name, unit) values (:ws, :name, :unit)
                        returning id""")
                .param("ws", workspaceId)
                .param("name", name)
                .param("unit", unit.name())
                .query(UUID.class)
                .single();
    }

    /** Removes a product made a moment ago that lost the race for its alias; nothing else points to it yet. */
    void discard(UUID workspaceId, UUID productId) {
        jdbc.sql("delete from products where workspace_id = :ws and id = :id")
                .param("ws", workspaceId)
                .param("id", productId)
                .update();
    }

    /** False when the market already had an alias for this description, which stays as it was. */
    boolean addAlias(UUID workspaceId, UUID marketId, String norm, UUID productId) {
        return jdbc.sql("""
                                insert into product_aliases (workspace_id, market_id, description_norm, product_id)
                                values (:ws, :market, :norm, :product)
                                on conflict (workspace_id, market_id, description_norm) do nothing""")
                        .param("ws", workspaceId)
                        .param("market", marketId)
                        .param("norm", norm)
                        .param("product", productId)
                        .update()
                > 0;
    }

    /**
     * The family's products whose normalized name contains the normalized {@code q}, by name, up to {@code limit}. An
     * empty {@code q} matches all. The comparison is made here because the database has no normalized name.
     */
    List<ProductRow> search(UUID workspaceId, String q, int limit) {
        String needle = TextNorm.normalize(q);
        return jdbc
                .sql("select id, name, unit, gtin from products where workspace_id = :ws order by name, id")
                .param("ws", workspaceId)
                .query((rs, n) -> new ProductRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("unit"),
                        rs.getString("gtin")))
                .stream()
                .filter(row ->
                        needle.isEmpty() || TextNorm.normalize(row.name()).contains(needle))
                .limit(limit)
                .toList();
    }
}
