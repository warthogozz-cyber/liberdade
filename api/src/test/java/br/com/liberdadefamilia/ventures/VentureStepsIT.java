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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Adding, ticking, renaming, moving and removing the steps of a project. */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import({TestcontainersConfiguration.class, VentureStepsIT.Config.class})
class VentureStepsIT {

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

    @Autowired
    ApplicationEvents events;

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
    void addGoesToTheEnd() {
        UUID venture = blank();

        var result = send(HttpMethod.POST, "/ventures/" + venture + "/steps", "{\"title\":\"Falar com o cliente\"}");

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.position").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Falar com o cliente");
        assertThat(result).bodyJson().extractingPath("$.doneAt").isNull();
        assertThat(positions(venture)).containsExactly(0, 1, 2, 3);
    }

    @Test
    void markAndUnmark() {
        UUID venture = blank();
        UUID step = stepIds(venture).get(0);

        var done = patch(venture, step, "{\"done\":true}");
        assertThat(done).hasStatus(200);
        assertThat(done).bodyJson().extractingPath("$.doneAt").isEqualTo("2026-11-20T12:00:00Z");

        var undone = patch(venture, step, "{\"done\":false}");
        assertThat(undone).hasStatus(200);
        assertThat(undone).bodyJson().extractingPath("$.doneAt").isNull();
    }

    @Test
    void moveRenumbers() {
        UUID venture = blank();
        add(venture, "Quarto");
        List<UUID> before = stepIds(venture);
        assertThat(before).hasSize(4);

        var result = patch(venture, before.get(3), "{\"position\":0}");

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.position").isEqualTo(0);
        assertThat(stepIds(venture)).containsExactly(before.get(3), before.get(0), before.get(1), before.get(2));
        assertThat(positions(venture)).containsExactly(0, 1, 2, 3);
    }

    @Test
    void moveOutOfRangeOrEmptyPatchIs422() {
        UUID venture = blank();
        UUID step = stepIds(venture).get(0);

        assertInvalid(patch(venture, step, "{\"position\":3}"));
        assertInvalid(patch(venture, step, "{\"position\":-1}"));
        assertInvalid(patch(venture, step, "{}"));
    }

    @Test
    void renameValidates() {
        UUID venture = blank();
        UUID step = stepIds(venture).get(0);

        assertInvalid(patch(venture, step, "{\"title\":\"\"}"));
        assertInvalid(patch(venture, step, "{\"title\":\"   \"}"));
        assertInvalid(patch(venture, step, "{\"title\":\"" + "a".repeat(81) + "\"}"));
        assertInvalid(send(HttpMethod.POST, "/ventures/" + venture + "/steps", "{\"title\":\"\"}"));
        assertInvalid(
                send(HttpMethod.POST, "/ventures/" + venture + "/steps", "{\"title\":\"" + "a".repeat(81) + "\"}"));

        var ok = patch(venture, step, "{\"title\":\"" + "a".repeat(80) + "\"}");
        assertThat(ok).hasStatus(200);
        assertThat(ok).bodyJson().extractingPath("$.title").isEqualTo("a".repeat(80));
    }

    @Test
    void twentyFirstStepIs422() {
        UUID venture = blank();
        for (int i = 3; i < 20; i++) {
            add(venture, "Passo " + i);
        }

        var result = send(HttpMethod.POST, "/ventures/" + venture + "/steps", "{\"title\":\"Um a mais\"}");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("venture.too-many-steps");
        assertThat(stepIds(venture)).hasSize(20);
    }

    @Test
    void deleteRenumbers() {
        UUID venture = blank();
        List<UUID> before = stepIds(venture);

        var result = send(HttpMethod.DELETE, "/ventures/" + venture + "/steps/" + before.get(1), null);

        assertThat(result).hasStatus(204);
        assertThat(stepIds(venture)).containsExactly(before.get(0), before.get(2));
        assertThat(positions(venture)).containsExactly(0, 1);
    }

    @Test
    void stepOfAnotherVentureIs404() {
        UUID venture = blank();
        UUID other = jdbc.sql("""
                        insert into ventures (workspace_id, name, type, currency)
                        values (:ws, 'Outro', 'OTHER', 'BRL') returning id""").param("ws", ws).query(UUID.class).single();
        UUID foreign = jdbc.sql("""
                        insert into venture_steps (workspace_id, venture_id, title, position)
                        values (:ws, :venture, 'De outro', 0) returning id""")
                .param("ws", ws)
                .param("venture", other)
                .query(UUID.class)
                .single();

        for (var result : List.of(
                patch(venture, foreign, "{\"done\":true}"),
                send(HttpMethod.DELETE, "/ventures/" + venture + "/steps/" + foreign, null),
                patch(venture, UUID.randomUUID(), "{\"done\":true}"),
                send(HttpMethod.POST, "/ventures/" + UUID.randomUUID() + "/steps", "{\"title\":\"X\"}"))) {
            assertThat(result).hasStatus(404);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("venture.not-found");
        }
        assertThat(stepIds(other)).containsExactly(foreign);
    }

    @Test
    void stepsOfAClosedVentureStayEditable() {
        UUID venture = blank();
        jdbc.sql(
                        "update ventures set status = 'CLOSED', started_on = date '2026-11-01', closed_on = date '2026-11-20' where id = :id")
                .param("id", venture)
                .update();

        assertThat(patch(venture, stepIds(venture).get(0), "{\"done\":true}")).hasStatus(200);
    }

    @Test
    void stepsDoNotBumpTheVersion() {
        UUID venture = blank();
        long before = version(venture);

        add(venture, "Outro");
        patch(venture, stepIds(venture).get(0), "{\"done\":true}");

        assertThat(version(venture)).isEqualTo(before);
    }

    @Test
    void stepsPublishVentureChanged() {
        UUID venture = blank();
        events.clear();
        add(venture, "Quarto");
        assertThat(changed(venture)).isEqualTo(1);

        UUID step = stepIds(venture).get(0);
        patch(venture, step, "{\"done\":true}");
        assertThat(changed(venture)).isEqualTo(2);

        send(HttpMethod.DELETE, "/ventures/" + venture + "/steps/" + step, null);
        assertThat(changed(venture)).isEqualTo(3);

        patch(venture, stepIds(venture).get(0), "{}");
        assertThat(changed(venture)).isEqualTo(3);
    }

    private long changed(UUID venture) {
        return events.stream(VentureChanged.class)
                .filter(e -> e.workspaceId().equals(ws) && e.ventureId().equals(venture))
                .count();
    }

    private long version(UUID venture) {
        return jdbc.sql("select version from ventures where id = :id")
                .param("id", venture)
                .query(Long.class)
                .single();
    }

    private UUID blank() {
        var result = send(HttpMethod.POST, "/ventures", "{\"name\":\"Meu projeto\",\"type\":\"OTHER\"}");
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(201);
        return UUID.fromString(JsonPath.read(body(result), "$.id"));
    }

    private void add(UUID venture, String title) {
        assertThat(send(HttpMethod.POST, "/ventures/" + venture + "/steps", "{\"title\":\"" + title + "\"}"))
                .hasStatus(201);
    }

    private List<UUID> stepIds(UUID venture) {
        return jdbc.sql("select id from venture_steps where venture_id = :id order by position")
                .param("id", venture)
                .query(UUID.class)
                .list();
    }

    private List<Integer> positions(UUID venture) {
        return jdbc.sql("select position from venture_steps where venture_id = :id order by position")
                .param("id", venture)
                .query(Integer.class)
                .list();
    }

    private static void assertInvalid(MvcTestResult result) {
        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("venture.invalid");
    }

    private MvcTestResult patch(UUID venture, UUID step, String body) {
        return send(HttpMethod.PATCH, "/ventures/" + venture + "/steps/" + step, body);
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
        request.with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }
}
