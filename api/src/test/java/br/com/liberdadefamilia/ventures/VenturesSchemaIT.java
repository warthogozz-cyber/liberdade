package br.com.liberdadefamilia.ventures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The V30 schema: status rules as CHECKs, family-scoped keys, cascade on the family, and the audit target. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VenturesSchemaIT {

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
    void closedNeedsClosedOn() {
        assertThatThrownBy(() -> venture(workspaceId, "CLOSED", "2026-10-01", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> venture(workspaceId, "CLOSED", "2026-10-01", "2026-10-02"))
                .doesNotThrowAnyException();
    }

    @Test
    void activeNeedsStartedOn() {
        assertThatThrownBy(() -> venture(workspaceId, "ACTIVE", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> venture(workspaceId, "PLANNING", null, null)).doesNotThrowAnyException();
    }

    @Test
    void itemOfAnotherFamilyIsRefused() {
        UUID other = workspaces.createFor(users.create("Bia Teste", "bia@example.com"));
        UUID venture = venture(workspaceId, "PLANNING", null, null);

        assertThatThrownBy(() ->
                        jdbc.sql("""
                                insert into venture_cost_items (workspace_id, venture_id, kind, name, amount, currency, position)
                                values (:ws, :v, 'INITIAL', 'Fogao', 100, 'BRL', 0)""").param("ws", other).param("v", venture).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingTheFamilyCascades() {
        UUID venture = venture(workspaceId, "PLANNING", null, null);
        jdbc.sql("""
                        insert into venture_cost_items (workspace_id, venture_id, kind, name, amount, currency, position)
                        values (:ws, :v, 'INITIAL', 'Fogao', 100, 'BRL', 0)""").param("ws", workspaceId).param("v", venture).update();
        jdbc.sql("""
                        insert into venture_products
                            (workspace_id, venture_id, name, unit_variable_cost, unit_price, planned_units_month, currency, position)
                        values (:ws, :v, 'Bolo', 5, 20, 30, 'BRL', 0)""").param("ws", workspaceId).param("v", venture).update();
        jdbc.sql("insert into venture_steps (workspace_id, venture_id, title, position) values (:ws, :v, 'Testar', 0)")
                .param("ws", workspaceId)
                .param("v", venture)
                .update();

        jdbc.sql("delete from workspaces where id = :ws")
                .param("ws", workspaceId)
                .update();

        for (String table : List.of("ventures", "venture_cost_items", "venture_products", "venture_steps")) {
            assertThat(jdbc.sql("select count(*) from " + table + " where workspace_id = :ws")
                            .param("ws", workspaceId)
                            .query(Long.class)
                            .single())
                    .as(table)
                    .isZero();
        }
    }

    @Test
    void auditAcceptsVenture() {
        for (String type : List.of(
                "USER",
                "MEMBERSHIP",
                "INVITATION",
                "WORKSPACE",
                "MODULE",
                "CONSENT",
                "ORDER",
                "PLAN",
                "RECURRING_RULE",
                "GOAL",
                "ACCOUNT",
                "VENTURE")) {
            assertThatCode(() -> jdbc.sql("insert into audit_events (action, target_type) values ('TEST', :t)")
                            .param("t", type)
                            .update())
                    .as(type)
                    .doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> jdbc.sql("insert into audit_events (action, target_type) values ('TEST', 'NOPE')")
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID venture(UUID ws, String status, String startedOn, String closedOn) {
        return jdbc.sql("""
                        insert into ventures (workspace_id, name, type, status, started_on, closed_on, currency)
                        values (:ws, 'Marmitas', 'FOOD', :status, cast(:started as date), cast(:closed as date), 'BRL')
                        returning id""")
                .param("ws", ws)
                .param("status", status)
                .param("started", startedOn)
                .param("closed", closedOn)
                .query(UUID.class)
                .single();
    }
}
