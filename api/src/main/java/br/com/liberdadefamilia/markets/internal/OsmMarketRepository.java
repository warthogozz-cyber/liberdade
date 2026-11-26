package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.OsmRef;
import br.com.liberdadefamilia.markets.domain.SearchBox;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The public OpenStreetMap extract ({@code osm_markets}, global) and the date of its last import. */
@Component
class OsmMarketRepository {

    /** The most one search returns from the extract. */
    static final int SEARCH_LIMIT = 500;

    static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final JdbcClient jdbc;

    OsmMarketRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The extract's markets inside the box, nearest to the center first, up to {@link #SEARCH_LIMIT}. A market the
     * family archived is left out; one the family has a row for carries that row's id.
     */
    List<OsmHit> inBox(UUID workspaceId, SearchBox box, BigDecimal centerLat, BigDecimal centerLon) {
        return jdbc.sql("""
                        select o.osm_type, o.osm_id, o.kind, o.name, o.brand, o.lat, o.lon, o.street, o.housenumber,
                               o.suburb, o.city, m.id as family_id
                        from osm_markets o
                        left join markets m on m.workspace_id = :ws and m.origin = 'OSM'
                                           and m.osm_type = o.osm_type and m.osm_id = o.osm_id
                        where o.lat between :minLat and :maxLat and o.lon between :minLon and :maxLon
                          and m.archived_at is null
                        order by power(o.lat - :cLat, 2)
                                   + power((o.lon - :cLon) * greatest(0.01, cos(radians(cast(:cLat as double precision)))), 2),
                                 o.osm_type, o.osm_id
                        limit :limit""")
                .param("ws", workspaceId)
                .param("minLat", box.minLat())
                .param("maxLat", box.maxLat())
                .param("minLon", box.minLon())
                .param("maxLon", box.maxLon())
                .param("cLat", centerLat)
                .param("cLon", centerLon)
                .param("limit", SEARCH_LIMIT)
                .query((rs, n) -> new OsmHit(
                        new OsmRef(rs.getString("osm_type").charAt(0), rs.getLong("osm_id")),
                        rs.getString("kind"),
                        rs.getString("name"),
                        rs.getString("brand"),
                        rs.getBigDecimal("lat"),
                        rs.getBigDecimal("lon"),
                        rs.getString("street"),
                        rs.getString("housenumber"),
                        rs.getString("suburb"),
                        rs.getString("city"),
                        rs.getObject("family_id", UUID.class)))
                .list();
    }

    Optional<OsmObject> find(OsmRef ref) {
        return jdbc.sql("""
                        select kind, name, brand, lat, lon, website, instagram, opening_hours, street, housenumber,
                               suburb, city
                        from osm_markets where osm_type = :type and osm_id = :osmId""")
                .param("type", String.valueOf(ref.type()))
                .param("osmId", ref.id())
                .query((rs, n) -> new OsmObject(
                        rs.getString("kind"),
                        rs.getString("name"),
                        rs.getString("brand"),
                        rs.getBigDecimal("lat"),
                        rs.getBigDecimal("lon"),
                        rs.getString("website"),
                        rs.getString("instagram"),
                        rs.getString("opening_hours"),
                        rs.getString("street"),
                        rs.getString("housenumber"),
                        rs.getString("suburb"),
                        rs.getString("city")))
                .optional();
    }

    /** The date of the data in OpenStreetMap, from the last import, as a date in São Paulo; empty before the first. */
    Optional<LocalDate> lastDatasetDate() {
        return jdbc.sql("select osm_timestamp from osm_market_imports order by id desc limit 1")
                .query((rs, n) ->
                        rs.getTimestamp(1).toInstant().atZone(SAO_PAULO).toLocalDate())
                .optional();
    }

    record OsmHit(
            OsmRef ref,
            String kind,
            @Nullable String name,
            @Nullable String brand,
            BigDecimal lat,
            BigDecimal lon,
            @Nullable String street,
            @Nullable String housenumber,
            @Nullable String suburb,
            @Nullable String city,
            @Nullable UUID familyId) {}

    record OsmObject(
            String kind,
            @Nullable String name,
            @Nullable String brand,
            BigDecimal lat,
            BigDecimal lon,
            @Nullable String website,
            @Nullable String instagram,
            @Nullable String openingHours,
            @Nullable String street,
            @Nullable String housenumber,
            @Nullable String suburb,
            @Nullable String city) {}
}
