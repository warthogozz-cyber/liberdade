package br.com.liberdadefamilia.markets;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Invented markets around invented points, for the Feira's integration tests. Nothing here is a real place. */
final class MarketsTestData {

    /** The test center: no fixture of another test is within 11 km of it. */
    static final String CENTER_LAT = "-23.00";

    static final String CENTER_LON = "-46.00";

    private MarketsTestData() {}

    static void clearExtract(JdbcClient jdbc) {
        jdbc.sql("delete from osm_markets").update();
        jdbc.sql("delete from osm_market_imports").update();
    }

    static void osm(JdbcClient jdbc, long id, @Nullable String name, String lat, String lon) {
        jdbc.sql("""
                        insert into osm_markets (osm_type, osm_id, kind, name, lat, lon, street, city)
                        values ('n', :id, 'SUPERMARKET', :name, cast(:lat as numeric), cast(:lon as numeric),
                                'Rua Exemplo', 'Cidade Exemplo')""")
                .param("id", id)
                .param("name", name)
                .param("lat", lat)
                .param("lon", lon)
                .update();
    }

    static void imported(JdbcClient jdbc, Instant osmTimestamp) {
        jdbc.sql("""
                        insert into osm_market_imports (osm_timestamp, row_count, sha256)
                        values (:at, 1, 'abc')""").param("at", Timestamp.from(osmTimestamp)).update();
    }

    /** A market of the family; {@code lat} and {@code lon} may be null (no position yet). */
    static UUID family(JdbcClient jdbc, UUID ws, String name, @Nullable String lat, @Nullable String lon) {
        return jdbc.sql("""
                        insert into markets (workspace_id, origin, name, kind, lat, lon)
                        values (:ws, 'FAMILY', :name, 'GREENGROCER', cast(:lat as numeric), cast(:lon as numeric))
                        returning id""")
                .param("ws", ws)
                .param("name", name)
                .param("lat", lat)
                .param("lon", lon)
                .query(UUID.class)
                .single();
    }

    /** An {@code OSM} row of the family for an object that is not in the extract (any more). */
    static UUID familyRowForOsm(JdbcClient jdbc, UUID ws, long osmId) {
        return jdbc.sql("""
                        insert into markets (workspace_id, origin, osm_type, osm_id, cnpj)
                        values (:ws, 'OSM', 'n', :id, '12345678000195')
                        returning id""")
                .param("ws", ws)
                .param("id", osmId)
                .query(UUID.class)
                .single();
    }

    static void archive(JdbcClient jdbc, UUID marketId) {
        jdbc.sql("update markets set archived_at = now() where id = :id")
                .param("id", marketId)
                .update();
    }
}
