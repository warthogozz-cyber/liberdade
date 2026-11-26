package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.FamilyRef;
import br.com.liberdadefamilia.markets.domain.GeoPoint;
import br.com.liberdadefamilia.markets.domain.MarketKind;
import br.com.liberdadefamilia.markets.domain.MarketRef;
import br.com.liberdadefamilia.markets.domain.OsmRef;
import br.com.liberdadefamilia.markets.domain.SearchBox;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
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
            + " flyer_set_at, archived_at, updated_at";

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

    /** The family's market with this CNPJ, archived or not; there is at most one ({@code markets_cnpj_once}). */
    Optional<MarketRow> findByCnpj(UUID workspaceId, String cnpj) {
        return jdbc.sql("select " + COLUMNS + " from markets where workspace_id = :ws and cnpj = :cnpj")
                .param("ws", workspaceId)
                .param("cnpj", cnpj)
                .query(MarketRepository::map)
                .optional();
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

    /** A new {@code FAMILY} row; {@code position} and {@code cnpj} may be null. {@code actor} is the membership. */
    UUID insertFamily(
            UUID workspaceId,
            UUID actor,
            String name,
            MarketKind kind,
            @Nullable GeoPoint position,
            @Nullable String cnpj) {
        return jdbc.sql("""
                        insert into markets (workspace_id, origin, name, kind, lat, lon, cnpj, created_by, updated_by)
                        values (:ws, 'FAMILY', :name, :kind, :lat, :lon, :cnpj, :actor, :actor)
                        returning id""")
                .param("ws", workspaceId)
                .param("name", name)
                .param("kind", kind.name())
                .param("lat", position == null ? null : position.lat(), Types.NUMERIC)
                .param("lon", position == null ? null : position.lon(), Types.NUMERIC)
                .param("cnpj", cnpj, Types.VARCHAR)
                .param("actor", actor)
                .query(UUID.class)
                .single();
    }

    /**
     * Gives the market the CNPJ of a receipt, only while it has none. False when it already has one (another save won
     * the race), and then nothing changes.
     */
    boolean setCnpjIfMissing(UUID workspaceId, UUID id, UUID actor, String cnpj) {
        return jdbc.sql("""
                                update markets set cnpj = :cnpj, updated_by = :actor, updated_at = now()
                                where workspace_id = :ws and id = :id and cnpj is null""")
                        .param("cnpj", cnpj)
                        .param("actor", actor)
                        .param("ws", workspaceId)
                        .param("id", id)
                        .update()
                > 0;
    }

    /**
     * Replaces the family's five links. A {@code *_set_at} moves only when its value changed, and goes back to null
     * with the value. The flyer's three fields change together.
     */
    void updateLinks(UUID workspaceId, UUID id, UUID actor, FamilyLinks links) {
        jdbc.sql("""
                        update markets set
                          website_set_at = case when cast(:website as text) is null then null
                                                when website is distinct from cast(:website as text) then now()
                                                else website_set_at end,
                          website = cast(:website as text),
                          instagram_set_at = case when cast(:instagram as text) is null then null
                                                  when instagram is distinct from cast(:instagram as text) then now()
                                                  else instagram_set_at end,
                          instagram = cast(:instagram as text),
                          flyer_set_at = case when cast(:flyerUrl as text) is null then null
                                              when flyer_url is distinct from cast(:flyerUrl as text)
                                                or flyer_source is distinct from cast(:flyerSource as text)
                                                or flyer_valid_until is distinct from cast(:validUntil as date)
                                              then now()
                                              else flyer_set_at end,
                          flyer_url = cast(:flyerUrl as text),
                          flyer_source = case when cast(:flyerUrl as text) is null then null
                                              else cast(:flyerSource as text) end,
                          flyer_valid_until = case when cast(:flyerUrl as text) is null then null
                                                   else cast(:validUntil as date) end,
                          updated_by = :actor, updated_at = now()
                        where workspace_id = :ws and id = :id""")
                .param("website", links.website(), Types.VARCHAR)
                .param("instagram", links.instagram(), Types.VARCHAR)
                .param("flyerUrl", links.flyerUrl(), Types.VARCHAR)
                .param("flyerSource", links.flyerSource(), Types.VARCHAR)
                .param("validUntil", links.flyerValidUntil(), Types.DATE)
                .param("actor", actor)
                .param("ws", workspaceId)
                .param("id", id)
                .update();
    }

    void updatePosition(UUID workspaceId, UUID id, UUID actor, GeoPoint position) {
        jdbc.sql("""
                        update markets set lat = :lat, lon = :lon, updated_by = :actor, updated_at = now()
                        where workspace_id = :ws and id = :id and origin = 'FAMILY'""")
                .param("lat", position.lat())
                .param("lon", position.lon())
                .param("actor", actor)
                .param("ws", workspaceId)
                .param("id", id)
                .update();
    }

    /** Hides the market; false when it already was, and then nothing changes (not even {@code archived_at}). */
    boolean archive(UUID workspaceId, UUID id, UUID actor) {
        return jdbc.sql("""
                                update markets set archived_at = now(), updated_by = :actor, updated_at = now()
                                where workspace_id = :ws and id = :id and archived_at is null""")
                        .param("actor", actor)
                        .param("ws", workspaceId)
                        .param("id", id)
                        .update()
                > 0;
    }

    /** Turns an {@code OSM} row into a {@code FAMILY} one: the OpenStreetMap ids go, the CNPJ and links stay. */
    void convertToFamily(
            UUID workspaceId, UUID id, UUID actor, String name, MarketKind kind, @Nullable GeoPoint position) {
        jdbc.sql("""
                        update markets set origin = 'FAMILY', osm_type = null, osm_id = null, name = :name,
                               kind = :kind, lat = :lat, lon = :lon, updated_by = :actor, updated_at = now()
                        where workspace_id = :ws and id = :id and origin = 'OSM'""")
                .param("name", name)
                .param("kind", kind.name())
                .param("lat", position == null ? null : position.lat(), Types.NUMERIC)
                .param("lon", position == null ? null : position.lon(), Types.NUMERIC)
                .param("actor", actor)
                .param("ws", workspaceId)
                .param("id", id)
                .update();
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

    /** The five links as stored: canonical text, or null for none. */
    record FamilyLinks(
            @Nullable String website,
            @Nullable String instagram,
            @Nullable String flyerUrl,
            @Nullable String flyerSource,
            @Nullable LocalDate flyerValidUntil) {}

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
                instant(rs, "archived_at"),
                instant(rs, "updated_at"));
    }

    private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
