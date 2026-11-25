package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.FamilyRef;
import br.com.liberdadefamilia.markets.domain.MarketRef;
import br.com.liberdadefamilia.markets.domain.OsmRef;
import br.com.liberdadefamilia.markets.domain.SearchBox;
import java.math.BigDecimal;
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

/** The family's own {@code markets} rows. Every query filters by the family (ADR-0008). */
@Component
public class MarketRepository {

    private static final String COLUMNS = "id, origin, osm_type, osm_id, name, kind, lat, lon, cnpj, website,"
            + " website_set_at, instagram, instagram_set_at, flyer_url, flyer_source, flyer_valid_until,"
            + " flyer_set_at, archived_at";

    private static final String NEAREST_FIRST = """
            power(lat - :cLat, 2)
              + power((lon - :cLon) * greatest(0.01, cos(radians(cast(:cLat as double precision)))), 2)""";

    private final JdbcClient jdbc;

    MarketRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The family's row for this reference, if any: by id, or by the OpenStreetMap object of an {@code OSM} row. */
    public Optional<MarketRow> findByRef(UUID workspaceId, MarketRef ref) {
        return switch (ref) {
            case FamilyRef family ->
                jdbc.sql("select " + COLUMNS + " from markets where workspace_id = :ws and id = :id")
                        .param("ws", workspaceId)
                        .param("id", family.id())
                        .query(MarketRepository::map)
                        .optional();
            case OsmRef osm ->
                jdbc.sql("select " + COLUMNS + " from markets where workspace_id = :ws and origin = 'OSM'"
                                + " and osm_type = :type and osm_id = :osmId")
                        .param("ws", workspaceId)
                        .param("type", String.valueOf(osm.type()))
                        .param("osmId", osm.id())
                        .query(MarketRepository::map)
                        .optional();
        };
    }

    /**
     * The id of the family's {@code OSM} row for this object, created when it is missing. {@code actor} is the
     * membership that caused it.
     *
     * @throws MarketNotFoundException when the object is not in the extract and the family has no row for it
     */
    public UUID ensureOsmRow(UUID workspaceId, OsmRef ref, UUID actor) {
        Optional<MarketRow> existing = findByRef(workspaceId, ref);
        if (existing.isPresent()) {
            return existing.get().id();
        }
        jdbc.sql("""
                        insert into markets (workspace_id, origin, osm_type, osm_id, created_by, updated_by)
                        select :ws, 'OSM', o.osm_type, o.osm_id, :actor, :actor
                        from osm_markets o
                        where o.osm_type = :type and o.osm_id = :osmId
                        on conflict (workspace_id, osm_type, osm_id) where origin = 'OSM' do nothing""")
                .param("ws", workspaceId)
                .param("actor", actor)
                .param("type", String.valueOf(ref.type()))
                .param("osmId", ref.id())
                .update();
        return findByRef(workspaceId, ref).map(MarketRow::id).orElseThrow(MarketNotFoundException::new);
    }

    /** Not archived {@code FAMILY} markets with a position inside the box, nearest to the center first. */
    List<NearbyFamily> familyInBox(UUID workspaceId, SearchBox box, BigDecimal centerLat, BigDecimal centerLon) {
        return jdbc.sql("""
                        select id, name, kind, lat, lon from markets
                        where workspace_id = :ws and origin = 'FAMILY' and archived_at is null
                          and lat between :minLat and :maxLat and lon between :minLon and :maxLon
                        order by\s""" + NEAREST_FIRST + ", id")
                .param("ws", workspaceId)
                .param("minLat", box.minLat())
                .param("maxLat", box.maxLat())
                .param("minLon", box.minLon())
                .param("maxLon", box.maxLon())
                .param("cLat", centerLat)
                .param("cLon", centerLon)
                .query((rs, n) -> new NearbyFamily(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("kind"),
                        rs.getBigDecimal("lat"),
                        rs.getBigDecimal("lon")))
                .list();
    }

    /** Not archived {@code FAMILY} markets without a position. */
    List<Unlocated> unlocated(UUID workspaceId) {
        return jdbc.sql("""
                        select id, name, kind from markets
                        where workspace_id = :ws and origin = 'FAMILY' and archived_at is null and lat is null
                        order by name, id""")
                .param("ws", workspaceId)
                .query((rs, n) ->
                        new Unlocated(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("kind")))
                .list();
    }

    /** Not archived {@code OSM} rows whose object is no longer in the extract. */
    List<Departed> departed(UUID workspaceId) {
        return jdbc.sql("""
                        select m.id, m.cnpj from markets m
                        where m.workspace_id = :ws and m.origin = 'OSM' and m.archived_at is null
                          and not exists (select 1 from osm_markets o
                                          where o.osm_type = m.osm_type and o.osm_id = m.osm_id)
                        order by m.created_at, m.id""")
                .param("ws", workspaceId)
                .query((rs, n) -> new Departed(rs.getObject("id", UUID.class), rs.getString("cnpj")))
                .list();
    }

    record NearbyFamily(UUID id, String name, @Nullable String kind, BigDecimal lat, BigDecimal lon) {}

    record Unlocated(UUID id, String name, @Nullable String kind) {}

    record Departed(UUID id, @Nullable String cnpj) {}

    private static MarketRow map(ResultSet rs, int row) throws SQLException {
        String osmType = rs.getString("osm_type");
        long osmId = rs.getLong("osm_id");
        Long osmIdOrNull = rs.wasNull() ? null : osmId;
        return new MarketRow(
                rs.getObject("id", UUID.class),
                rs.getString("origin"),
                osmType == null ? null : osmType.charAt(0),
                osmIdOrNull,
                rs.getString("name"),
                rs.getString("kind"),
                rs.getBigDecimal("lat"),
                rs.getBigDecimal("lon"),
                rs.getString("cnpj"),
                rs.getString("website"),
                instant(rs, "website_set_at"),
                rs.getString("instagram"),
                instant(rs, "instagram_set_at"),
                rs.getString("flyer_url"),
                rs.getString("flyer_source"),
                rs.getObject("flyer_valid_until", LocalDate.class),
                instant(rs, "flyer_set_at"),
                instant(rs, "archived_at"));
    }

    private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
