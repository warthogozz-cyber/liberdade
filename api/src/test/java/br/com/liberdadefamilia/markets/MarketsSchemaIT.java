package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The V40 schema: one origin per row, one row per OpenStreetMap object and family, kinds, and the new keys. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MarketsSchemaIT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    private UUID workspaceId;

    @BeforeEach
    void family() {
        workspaceId = workspaces.createFor(users.create("Ana Teste", "ana@example.com"));
    }

    @Test
    void checkRejectsMixedOrigin() {
        assertThatThrownBy(() -> insert(workspaceId, "OSM", "'n'", "1", "'Mercado Exemplo'", "null"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(workspaceId, "OSM", "'n'", "1", "null", "-10.00001"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(workspaceId, "FAMILY", "null", "null", "null", "null"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(workspaceId, "FAMILY", "'n'", "1", "'Mercado Exemplo'", "null"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatCode(() -> insert(workspaceId, "OSM", "'n'", "1", "null", "null"))
                .doesNotThrowAnyException();
        assertThatCode(() -> insert(workspaceId, "FAMILY", "null", "null", "'Mercado Exemplo'", "null"))
                .doesNotThrowAnyException();
    }

    @Test
    void osmObjectIsOncePerFamily() {
        insert(workspaceId, "OSM", "'w'", "45", "null", "null");

        assertThatThrownBy(() -> insert(workspaceId, "OSM", "'w'", "45", "null", "null"))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID other = workspaces.createFor(users.create("Bia Teste", "bia@example.com"));
        assertThatCode(() -> insert(other, "OSM", "'w'", "45", "null", "null")).doesNotThrowAnyException();
    }

    @Test
    void kindCheck() {
        for (String kind : new String[] {"SUPERMARKET", "CONVENIENCE", "GREENGROCER", "MARKETPLACE"}) {
            assertThatCode(() -> jdbc.sql("""
                            insert into markets (workspace_id, origin, name, kind)
                            values (:ws, 'FAMILY', 'Mercado Exemplo', :kind)""")
                            .param("ws", workspaceId)
                            .param("kind", kind)
                            .update())
                    .as(kind)
                    .doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> jdbc.sql("""
                        insert into markets (workspace_id, origin, name, kind)
                        values (:ws, 'FAMILY', 'Mercado Exemplo', 'KIOSK')""").param("ws", workspaceId).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("""
                        insert into osm_markets (osm_type, osm_id, kind, lat, lon)
                        values ('n', 1, 'KIOSK', -10, -50)""").update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void moduleKeyAccepted() {
        assertThatCode(() -> jdbc.sql("""
                        insert into workspace_modules (workspace_id, module_key, enabled)
                        values (:ws, 'markets', true)""").param("ws", workspaceId).update()).doesNotThrowAnyException();
        assertThatThrownBy(() -> jdbc.sql("""
                        insert into workspace_modules (workspace_id, module_key, enabled)
                        values (:ws, 'nope', true)""").param("ws", workspaceId).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditAcceptsMarket() {
        assertThatCode(() -> jdbc.sql("insert into audit_events (action, target_type) values ('TEST', 'MARKET')")
                        .update())
                .doesNotThrowAnyException();
        assertThatCode(() -> jdbc.sql("insert into audit_events (action, target_type) values ('TEST', 'VENTURE')")
                        .update())
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> jdbc.sql("insert into audit_events (action, target_type) values ('TEST', 'NOPE')")
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingTheFamilyCascades() {
        insert(workspaceId, "FAMILY", "null", "null", "'Mercado Exemplo'", "null");
        jdbc.sql("insert into market_home (workspace_id, lat, lon) values (:ws, -10.001, -50.001)")
                .param("ws", workspaceId)
                .update();

        jdbc.sql("delete from workspaces where id = :ws")
                .param("ws", workspaceId)
                .update();

        for (String table : new String[] {"markets", "market_home"}) {
            assertThat(jdbc.sql("select count(*) from " + table + " where workspace_id = :ws")
                            .param("ws", workspaceId)
                            .query(Long.class)
                            .single())
                    .as(table)
                    .isZero();
        }
    }

    /** The literals are fixed test values written as SQL ({@code null} or a quoted char), never user input. */
    private int insert(UUID ws, String origin, String osmType, String osmId, String name, String lat) {
        return jdbc.sql(
                        "insert into markets (workspace_id, origin, osm_type, osm_id, name, lat, lon) values (:ws, :origin, "
                                + osmType + ", " + osmId + ", " + name + ", " + lat + ", " + lat + ")")
                .param("ws", ws)
                .param("origin", origin)
                .update();
    }
}
