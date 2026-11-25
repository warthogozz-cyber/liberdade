package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.ExportedMarket;
import br.com.liberdadefamilia.markets.ExportedMarketHome;
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
    public Optional<ExportedMarketHome> home(UUID workspaceId) {
        return jdbc.sql("select lat, lon, label from market_home where workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .query((rs, row) ->
                        new ExportedMarketHome(rs.getBigDecimal("lat"), rs.getBigDecimal("lon"), rs.getString("label")))
                .optional();
    }
}
