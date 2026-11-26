package br.com.liberdadefamilia.markets.internal;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The family's {@code price_records}. Every query filters by the family. */
@Component
class PriceRepository {

    private final JdbcClient jdbc;

    PriceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The newest price of a product at a market. */
    record LatestPrice(UUID productId, String name, String unit, BigDecimal unitPrice, LocalDate observedOn) {}

    void insert(
            UUID workspaceId,
            UUID marketId,
            UUID productId,
            UUID receiptId,
            LocalDate observedOn,
            String unit,
            @Nullable BigDecimal quantity,
            BigDecimal unitPrice,
            UUID createdBy) {
        jdbc.sql("""
                        insert into price_records
                            (workspace_id, market_id, product_id, receipt_id, observed_on, unit, quantity, unit_price,
                             created_by)
                        values (:ws, :market, :product, :receipt, :observedOn, :unit, :quantity, :price, :actor)""")
                .param("ws", workspaceId)
                .param("market", marketId)
                .param("product", productId)
                .param("receipt", receiptId)
                .param("observedOn", observedOn)
                .param("unit", unit)
                .param("quantity", quantity, Types.NUMERIC)
                .param("price", unitPrice)
                .param("actor", createdBy)
                .update();
    }

    /** The last price of each product at the market, by product name, up to {@code limit}. */
    List<LatestPrice> latestByMarket(UUID workspaceId, UUID marketId, int limit) {
        return jdbc.sql("""
                        select * from (
                            select distinct on (r.product_id)
                                   r.product_id, p.name, r.unit, r.unit_price, r.observed_on
                            from price_records r
                            join products p on p.workspace_id = r.workspace_id and p.id = r.product_id
                            where r.workspace_id = :ws and r.market_id = :market
                            order by r.product_id, r.observed_on desc, r.created_at desc, r.id desc) latest
                        order by name, product_id
                        limit :limit""")
                .param("ws", workspaceId)
                .param("market", marketId)
                .param("limit", limit)
                .query((rs, n) -> new LatestPrice(
                        rs.getObject("product_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("unit"),
                        rs.getBigDecimal("unit_price"),
                        rs.getObject("observed_on", LocalDate.class)))
                .list();
    }
}
