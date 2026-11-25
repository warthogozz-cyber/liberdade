package br.com.liberdadefamilia.audit;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Closing and deleting a project leave a trace with no value and no name; creating and editing leave none. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, VentureAuditIT.Config.class})
class VentureAuditIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    private record Row(
            String action, String targetType, @Nullable UUID targetId, UUID actorUserId, String metadata) {}

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestClock clock;

    private UUID ana;
    private UUID ws;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-20T12:00:00Z"));
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'ventures', true)")
                .param("ws", ws)
                .update();
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void closeAndDeleteAreAudited() {
        var created = send(HttpMethod.POST, "/ventures", "{\"templateKey\":\"food-sweets\",\"name\":\"Doces\"}");
        assertThat(created).hasStatus(201);
        UUID venture = UUID.fromString(JsonPath.read(body(created), "$.id"));
        assertThat(send(HttpMethod.PUT, "/ventures/" + venture, """
                        {"version":0,"name":"Outro nome","type":"FOOD","startedOn":null,
                         "initialItems":[],"fixedItems":[],"products":[]}""")).hasStatus(200);
        assertThat(rows()).isEmpty();

        assertThat(send(
                        HttpMethod.POST,
                        "/ventures/" + venture + "/status",
                        "{\"version\":1,\"status\":\"CLOSED\",\"on\":null}"))
                .hasStatus(200);
        assertThat(send(HttpMethod.DELETE, "/ventures/" + venture, null)).hasStatus(204);

        List<Row> rows = rows();
        assertThat(rows).extracting(Row::action).containsExactly("venture.closed", "venture.deleted");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.targetType()).isEqualTo("VENTURE");
            assertThat(row.targetId()).isEqualTo(venture);
            assertThat(row.actorUserId()).isEqualTo(ana);
            assertThat(row.metadata()).isEqualTo("{}");
        });
    }

    private List<Row> rows() {
        return jdbc.sql("""
                        select action, target_type, target_id, actor_user_id, metadata::text as metadata
                        from audit_events
                        where workspace_id = :ws and action like 'venture.%'
                        order by occurred_at, id""")
                .param("ws", ws)
                .query((rs, row) -> new Row(
                        rs.getString("action"),
                        rs.getString("target_type"),
                        rs.getObject("target_id", UUID.class),
                        rs.getObject("actor_user_id", UUID.class),
                        rs.getString("metadata")))
                .list();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult send(HttpMethod method, String path, @Nullable String body) {
        var request = mvc.method(method).uri("/v1/workspaces/" + ws + path).with(TestUsers.signedIn(ana, null));
        if (method != HttpMethod.GET) {
            request.with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)));
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }
}
