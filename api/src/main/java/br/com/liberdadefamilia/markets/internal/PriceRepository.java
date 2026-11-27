package br.com.liberdadefamilia.markets.internal;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    /** How a market is named in paths and JSON: an OpenStreetMap object or the id of the family row. */
    private static final String MARKET_REF =
            "case when m.origin = 'OSM' then 'osm-' || m.osm_type || '-' || m.osm_id else m.id::text end";

    private static final String MARKET_NAME = "coalesce(m.name, o.name)";
    private static final String MARKET_JOIN = """
            join markets m on m.workspace_id = r.workspace_id and m.id = r.market_id
            left join osm_markets o on o.osm_type = m.osm_type and o.osm_id = m.osm_id""";

    private final JdbcClient jdbc;

    PriceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The newest price of a product at a market; {@code marketName} is null for an OpenStreetMap market with no name. */
    record LatestPrice(
            UUID productId,
            String name,
            String unit,
            BigDecimal unitPrice,
            LocalDate observedOn,
            UUID marketId,
            String marketRef,
            @Nullable String marketName) {}

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
                                   r.product_id, p.name, r.unit, r.unit_price, r.observed_on, r.market_id,
                                   %s as market_ref, %s as market_name
                            from price_records r
                            join products p on p.workspace_id = r.workspace_id and p.id = r.product_id
                            %s
                            where r.workspace_id = :ws and r.market_id = :market
                            order by r.product_id, r.observed_on desc, r.created_at desc, r.id desc) latest
                        order by name, product_id
                        limit :limit""".formatted(MARKET_REF, MARKET_NAME, MARKET_JOIN))
                .param("ws", workspaceId)
                .param("market", marketId)
                .param("limit", limit)
                .query(PriceRepository::latest)
                .list();
    }

    /**
     * The last price of each product at each market of the family, by product name and then market name. "Last" is the
     * same rule as {@link #latestByMarket}: the newest day, then the newest row.
     */
    List<LatestPrice> latestPerProductAndMarket(UUID workspaceId) {
        return jdbc.sql("""
                        select * from (
                            select distinct on (r.product_id, r.market_id)
                                   r.product_id, p.name, r.unit, r.unit_price, r.observed_on, r.market_id,
                                   %s as market_ref, %s as market_name
                            from price_records r
                            join products p on p.workspace_id = r.workspace_id and p.id = r.product_id
                            %s
                            where r.workspace_id = :ws
                            order by r.product_id, r.market_id, r.observed_on desc, r.created_at desc, r.id desc) latest
                        order by name, product_id, market_name nulls last, market_id""".formatted(MARKET_REF, MARKET_NAME, MARKET_JOIN))
                .param("ws", workspaceId)
                .query(PriceRepository::latest)
                .list();
    }

    private static LatestPrice latest(ResultSet rs, int row) throws SQLException {
        return new LatestPrice(
                rs.getObject("product_id", UUID.class),
                rs.getString("name"),
                rs.getString("unit"),
                rs.getBigDecimal("unit_price"),
                rs.getObject("observed_on", LocalDate.class),
                rs.getObject("market_id", UUID.class),
                rs.getString("market_ref"),
                rs.getString("market_name"));
    }
}
