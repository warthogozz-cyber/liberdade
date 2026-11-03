package br.com.liberdadefamilia.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class WorkspaceIT {

    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    ApplicationEvents events;

    @Test
    void createsFamily() {
        UUID userId = users.create("Ana Teste", "ana@example.com");

        var result = create(userId, "{\"name\":\"  Família Teste  \",\"timezone\":\"America/Manaus\"}");

        assertThat(result).hasStatus(201).hasContentType(MediaType.APPLICATION_JSON);
        assertThat(result).headers().doesNotContainHeader("Location");
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"name":"Família Teste","role":"OWNER"}""");
        UUID workspaceId = idOf(result);
        Map<String, Object> workspace = jdbc.sql("select name, timezone, base_currency from workspaces where id = :id")
                .param("id", workspaceId)
                .query()
                .singleRow();
        assertThat(workspace)
                .containsEntry("name", "Família Teste")
                .containsEntry("timezone", "America/Manaus")
                .containsEntry("base_currency", "BRL");
        Map<String, Object> membership =
                jdbc.sql("""
                        select user_id, role, status, nickname
                        from memberships
                        where workspace_id = :id""").param("id", workspaceId).query().singleRow();
        assertThat(membership)
                .containsEntry("user_id", userId)
                .containsEntry("role", "OWNER")
                .containsEntry("status", "ACTIVE")
                .containsEntry("nickname", "Ana");
    }

    @Test
    void unknownTimezoneFallsBack() {
        UUID userId = users.create("Ana Teste", "ana@example.com");

        var result = create(userId, "{\"name\":\"Família Teste\",\"timezone\":\"Mars/Olympus\"}");

        assertThat(result).hasStatus(201);
        assertThat(timezoneOf(idOf(result))).isEqualTo("America/Fortaleza");
    }

    @Test
    void missingTimezoneFallsBack() {
        UUID userId = users.create("Ana Teste", "ana@example.com");

        var result = create(userId, "{\"name\":\"Família Teste\"}");

        assertThat(result).hasStatus(201);
        assertThat(timezoneOf(idOf(result))).isEqualTo("America/Fortaleza");
    }

    @Test
    void blankNameIs422() {
        UUID userId = users.create("Ana Teste", "ana@example.com");

        var result = create(userId, "{\"name\":\"   \"}");

        assertThat(result).hasStatus(422).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"code":"request.invalid","errors":[{"field":"name","code":"NotBlank"}]}""");
        assertThat(activeMemberships(userId)).isZero();
    }

    @Test
    void nameUpTo60() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        UUID otherId = users.create("Bia Teste", "bia@example.com");

        var tooLong = create(userId, "{\"name\":\"" + "F".repeat(61) + "\"}");
        var longest = create(otherId, "{\"name\":\"" + "F".repeat(60) + "\"}");

        assertThat(tooLong).hasStatus(422);
        assertThat(tooLong).bodyJson().isLenientlyEqualTo("""
                {"code":"request.invalid","errors":[{"field":"name","code":"Size"}]}""");
        assertThat(longest).hasStatus(201);
        assertThat(longest).bodyJson().extractingPath("$.name").isEqualTo("F".repeat(60));
    }

    @Test
    void secondFamilyIs409() {
        UUID userId = users.create("Ana Teste", "ana@example.com");

        var first = create(userId, "{\"name\":\"Família Teste\"}");
        var second = create(userId, "{\"name\":\"Outra Família\"}");

        assertThat(first).hasStatus(201);
        assertThat(second).hasStatus(409).hasContentType(PROBLEM_JSON);
        assertThat(second).bodyJson().isLenientlyEqualTo("""
                {"code":"workspace.already-exists","detail":"Você já faz parte de uma família."}""");
        assertThat(activeMemberships(userId)).isOne();
    }

    /** A double tap on "Começar": the same session posts twice at once, and only one family comes out. */
    @Test
    void concurrentCreateMakesOneFamily() throws Exception {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        var csrf = CsrfSession.start(mvc, TestUsers.signedIn(userId, null));
        long workspacesBefore = countWorkspaces();
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        Callable<Integer> tap = () -> {
            var request = mvc.post()
                    .uri("/v1/workspaces")
                    .with(TestUsers.signedIn(userId, null))
                    .with(csrf)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Família Teste\"}");
            ready.countDown();
            go.await();
            return request.exchange().getResponse().getStatus();
        };

        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = pool.submit(tap);
            Future<Integer> second = pool.submit(tap);
            ready.await();
            go.countDown();
            statuses.add(first.get());
            statuses.add(second.get());
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(countWorkspaces() - workspacesBefore).isOne();
        assertThat(activeMemberships(userId)).isOne();
    }

    @Test
    void publishesWorkspaceCreated() {
        UUID userId = users.create("Ana Teste", "ana@example.com");

        UUID workspaceId = idOf(create(userId, "{\"name\":\"Família Teste\"}"));

        UUID ownerMembershipId = jdbc.sql("select id from memberships where workspace_id = :id")
                .param("id", workspaceId)
                .query(UUID.class)
                .single();
        assertThat(events.stream(WorkspaceCreated.class))
                .containsExactly(new WorkspaceCreated(workspaceId, ownerMembershipId, Currency.getInstance("BRL")));
    }

    @Test
    void requiresCsrf() {
        UUID userId = users.create("Ana Teste", "ana@example.com");

        var result = mvc.post()
                .uri("/v1/workspaces")
                .with(TestUsers.signedIn(userId, null))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Família Teste\"}")
                .exchange();

        assertThat(result).hasStatus(403).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("csrf.invalid");
        assertThat(activeMemberships(userId)).isZero();
    }

    private MvcTestResult create(UUID userId, String body) {
        return mvc.post()
                .uri("/v1/workspaces")
                .with(TestUsers.signedIn(userId, "Teste"))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(userId, "Teste")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private String timezoneOf(UUID workspaceId) {
        return jdbc.sql("select timezone from workspaces where id = :id")
                .param("id", workspaceId)
                .query(String.class)
                .single();
    }

    private long activeMemberships(UUID userId) {
        return jdbc.sql("select count(*) from memberships where user_id = :userId and status = 'ACTIVE'")
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    private long countWorkspaces() {
        return jdbc.sql("select count(*) from workspaces").query(Long.class).single();
    }

    private static UUID idOf(MvcTestResult result) {
        try {
            return UUID.fromString(
                    JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id"));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
