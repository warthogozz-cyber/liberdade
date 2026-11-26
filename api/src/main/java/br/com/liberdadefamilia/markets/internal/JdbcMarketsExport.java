package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.ExportedAlias;
import br.com.liberdadefamilia.markets.ExportedMarket;
import br.com.liberdadefamilia.markets.ExportedMarketHome;
import br.com.liberdadefamilia.markets.ExportedPrice;
import br.com.liberdadefamilia.markets.ExportedProduct;
import br.com.liberdadefamilia.markets.ExportedReceipt;
import br.com.liberdadefamilia.markets.MarketsExport;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads the family's markets and address for the data export. It never touches {@code osm_markets}. */
@Component
class JdbcMarketsExport implements MarketsExport {

    private final JdbcClient jdbc;

    JdbcMarketsExport(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ExportedMarket> familyMarkets(UUID workspaceId) {
        return jdbc.sql("""
                        select id, origin, osm_type, osm_id, name, kind, lat, lon, cnpj, website, instagram,
                               flyer_url, flyer_source, flyer_valid_until, archived_at
                        from markets
                        where workspace_id = :workspaceId
                        order by created_at, id""")
                .param("workspaceId", workspaceId)
                .query((rs, row) -> {
                    String osmType = rs.getString("osm_type");
                    long osmId = rs.getLong("osm_id");
                    String osmRef = osmType == null ? null : "osm-" + osmType + "-" + osmId;
                    LocalDate validUntil = rs.getObject("flyer_valid_until", LocalDate.class);
                    Timestamp archivedAt = rs.getTimestamp("archived_at");
                    return new ExportedMarket(
                            rs.getObject("id", UUID.class),
                            rs.getString("origin"),
                            osmRef,
                            rs.getString("name"),
                            rs.getString("kind"),
                            rs.getBigDecimal("lat"),
                            rs.getBigDecimal("lon"),
                            rs.getString("cnpj"),
                            rs.getString("website"),
                            rs.getString("instagram"),
                            rs.getString("flyer_url"),
                            rs.getString("flyer_source"),
                            validUntil,
                            archivedAt == null ? null : archivedAt.toInstant());
                })
                .list();
    }

    @Override
    public List<ExportedProduct> products(UUID workspaceId) {
        return jdbc.sql("""
                        select id, name, unit, gtin, created_at
                        from products
                        where workspace_id = :workspaceId
                        order by created_at, id""")
                .param("workspaceId", workspaceId)
                .query((rs, row) -> new ExportedProduct(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("unit"),
                        rs.getString("gtin"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    @Override
    public List<ExportedAlias> aliases(UUID workspaceId) {
        return jdbc.sql("""
                        select market_id, description_norm, product_id
                        from product_aliases
                        where workspace_id = :workspaceId
                        order by market_id, description_norm""")
                .param("workspaceId", workspaceId)
                .query((rs, row) -> new ExportedAlias(
                        rs.getObject("market_id", UUID.class),
                        rs.getString("description_norm"),
                        rs.getObject("product_id", UUID.class)))
                .list();
    }

    @Override
    public List<ExportedPrice> prices(UUID workspaceId) {
        return jdbc.sql("""
                        select product_id, market_id, receipt_id, observed_on, unit, quantity, unit_price
                        from price_records
                        where workspace_id = :workspaceId
                        order by observed_on, created_at, id""")
                .param("workspaceId", workspaceId)
                .query((rs, row) -> new ExportedPrice(
                        rs.getObject("product_id", UUID.class),
                        rs.getObject("market_id", UUID.class),
                        rs.getObject("receipt_id", UUID.class),
                        rs.getObject("observed_on", LocalDate.class),
                        rs.getString("unit"),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("unit_price")))
                .list();
    }

    @Override
    public List<ExportedReceipt> receipts(UUID workspaceId) {
        return jdbc.sql("""
                        select id, market_id, issued_on, mode, item_count, created_at
                        from receipts
                        where workspace_id = :workspaceId
                        order by created_at, id""")
                .param("workspaceId", workspaceId)
                .query((rs, row) -> new ExportedReceipt(
                        rs.getObject("id", UUID.class),
                        rs.getObject("market_id", UUID.class),
                        rs.getObject("issued_on", LocalDate.class),
                        rs.getString("mode"),
                        rs.getInt("item_count"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    @Override
    public Optional<ExportedMarketHome> home(UUID workspaceId) {
        return jdbc.sql("select lat, lon, label from market_home where workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .query((rs, row) ->
                        new ExportedMarketHome(rs.getBigDecimal("lat"), rs.getBigDecimal("lon"), rs.getString("label")))
                .optional();
    }
}
