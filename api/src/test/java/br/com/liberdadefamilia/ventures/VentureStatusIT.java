package br.com.liberdadefamilia.ventures;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
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

/** The status table: the days it writes, what it refuses and the plan limit on reopening. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, VentureStatusIT.Config.class})
class VentureStatusIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

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
        // 02:00 UTC on the 21st is still the 20th in the family's zone (UTC-3).
        clock.freeze(Instant.parse("2026-11-21T02:00:00Z"));
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
    void activateRecordsStartedOn() {
        UUID first = create();
        var today = status(first, 0, "ACTIVE", null);
        assertThat(today).hasStatus(200);
        assertThat(today).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(startedOn(first)).isEqualTo(LocalDate.parse("2026-11-20"));

        UUID second = insertDirectly();
        assertThat(status(second, 0, "ACTIVE", "2026-11-05")).hasStatus(200);
        assertThat(startedOn(second)).isEqualTo(LocalDate.parse("2026-11-05"));
    }

    @Test
    void closeFromPlanning() {
        UUID venture = create();

        var closed = status(venture, 0, "CLOSED", "2026-11-18");

        assertThat(closed).hasStatus(200);
        assertThat(startedOn(venture)).isEqualTo(LocalDate.parse("2026-11-18"));
        assertThat(closedOn(venture)).isEqualTo(LocalDate.parse("2026-11-18"));
    }

    @Test
    void closeBeforeStartIs422() {
        UUID venture = create();
        assertThat(status(venture, 0, "ACTIVE", "2026-11-10")).hasStatus(200);

        var closed = status(venture, 1, "CLOSED", "2026-11-05");

        assertThat(closed).hasStatus(422);
        assertThat(closed).bodyJson().extractingPath("$.code").isEqualTo("venture.started-on-invalid");
    }

    @Test
    void futureDayIs422() {
        UUID venture = create();

        var result = status(venture, 0, "ACTIVE", "2026-11-21");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("venture.started-on-invalid");
    }

    @Test
    void reopenClearsClosedOnAndChecksTheLimit() {
        UUID first = create();
        assertThat(status(first, 0, "CLOSED", null)).hasStatus(200);
        assertThat(closedOn(first)).isNotNull();

        var reopened = status(first, 1, "PAUSED", null);
        assertThat(reopened).hasStatus(200);
        assertThat(reopened).bodyJson().extractingPath("$.closedOn").isNull();
        assertThat(closedOn(first)).isNull();

        assertThat(status(first, 2, "CLOSED", null)).hasStatus(200);
        create();

        var blocked = status(first, 3, "PAUSED", null);
        assertThat(blocked).hasStatus(403);
        assertThat(blocked).bodyJson().extractingPath("$.code").isEqualTo("venture.limit-reached");
        assertThat(closedOn(first)).isNotNull();
    }

    @Test
    void invalidTransitionIs422() {
        UUID venture = create();
        assertThat(status(venture, 0, "CLOSED", null)).hasStatus(200);

        var result = status(venture, 1, "ACTIVE", null);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("venture.status-invalid");
        assertThat(status(venture, 1, "PLANNING", null)).hasStatus(422);
    }

    @Test
    void staleVersionIs409() {
        UUID venture = create();

        var result = status(venture, 5, "ACTIVE", null);

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("venture.version-conflict");
    }

    // --- helpers ---

    /** A project through the API; the Grátis limit lets one stay open at a time. */
    private UUID create() {
        var result = send(HttpMethod.POST, "/ventures", "{\"name\":\"Projeto\",\"type\":\"OTHER\"}");
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(201);
        return UUID.fromString(JsonPath.read(body(result), "$.id"));
    }

    /** A project inserted directly, for the test that needs two open ones. */
    private UUID insertDirectly() {
        UUID membership = workspaces.membershipOf(ws, ana);
        return jdbc.sql("""
                        insert into ventures (workspace_id, name, type, currency, created_by)
                        values (:ws, 'Outro', 'OTHER', 'BRL', :m)
                        returning id""")
                .param("ws", ws)
                .param("m", membership)
                .query(UUID.class)
                .single();
    }

    private MvcTestResult status(UUID venture, long version, String status, @Nullable String on) {
        String day = on == null ? "null" : "\"" + on + "\"";
        return send(
                HttpMethod.POST,
                "/ventures/" + venture + "/status",
                "{\"version\":%d,\"status\":\"%s\",\"on\":%s}".formatted(version, status, day));
    }

    private @Nullable LocalDate startedOn(UUID venture) {
        return column(venture, "started_on");
    }

    private @Nullable LocalDate closedOn(UUID venture) {
        return column(venture, "closed_on");
    }

    private @Nullable LocalDate column(UUID venture, String column) {
        Date date = jdbc.sql("select " + column + " from ventures where id = :id")
                .param("id", venture)
                .query(Date.class)
                .optional()
                .orElse(null);
        return date == null ? null : date.toLocalDate();
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
