package br.com.liberdadefamilia.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.MemberRoleChanged;
import br.com.liberdadefamilia.workspace.Memberships;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * What the audit trail records for each sensitive action, and what it never keeps. Its own context, since the sign-in
 * test needs the mock Google provider, whose address is only known at runtime.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuditIT.Config.class})
class AuditIT {

    private static final String SESSION_COOKIE = "__Host-sid";
    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger();
    private static final String CHROME_ON_ANDROID = "Mozilla/5.0 (Linux; Android 14; Pixel 8) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";

    private static final GenericContainer<?> GOOGLE = new GenericContainer<>(
                    DockerImageName.parse("ghcr.io/navikt/mock-oauth2-server:6.1.3"))
            .withEnv("JSON_CONFIG", "{\"interactiveLogin\":true}")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/google/.well-known/openid-configuration"));

    @DynamicPropertySource
    static void google(DynamicPropertyRegistry registry) {
        GOOGLE.start();
        registry.add(
                "spring.security.oauth2.client.provider.google.issuer-uri",
                () -> "http://" + GOOGLE.getHost() + ":" + GOOGLE.getMappedPort(8080) + "/google");
    }

    /**
     * Throws when armed, after looking in the same transaction for the audit row of the change. {@code auditRowsSeen}
     * proves the row had been written before the failure, so the test shows a rollback and not a missing insert.
     */
    static class FailAfterAudit {

        private final AtomicBoolean armed = new AtomicBoolean();
        private final AtomicInteger auditRowsSeen = new AtomicInteger(-1);
        private final JdbcClient jdbc;

        FailAfterAudit(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        void arm() {
            auditRowsSeen.set(-1);
            armed.set(true);
        }

        void disarm() {
            armed.set(false);
        }

        int auditRowsSeen() {
            return auditRowsSeen.get();
        }

        @EventListener
        @Order(Ordered.LOWEST_PRECEDENCE)
        void on(MemberRoleChanged event) {
            if (armed.get()) {
                auditRowsSeen.set(jdbc.sql("""
                                select count(*) from audit_events
                                where action = 'member.role_changed' and target_id = :id""")
                        .param("id", event.membershipId())
                        .query(Integer.class)
                        .single());
                throw new IllegalStateException("forced failure");
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        FailAfterAudit failAfterAudit(JdbcClient jdbc) {
            return new FailAfterAudit(jdbc);
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
    Memberships memberships;

    @Autowired
    FailAfterAudit failAfterAudit;

    @AfterEach
    void disarm() {
        failAfterAudit.disarm();
    }

    @Test
    void signInIsAudited() throws Exception {
        var start = mvc.get().uri("/oauth2/authorization/google").exchange();
        Cookie anonymous = start.getResponse().getCookie(SESSION_COOKIE);
        assertThat(anonymous).isNotNull();
        String callback = signInAtProvider(start.getResponse().getHeader(HttpHeaders.LOCATION), "audit-login");

        var signedIn = mvc.get()
                .uri(URI.create(callback))
                .cookie(anonymous)
                .header(HttpHeaders.USER_AGENT, CHROME_ON_ANDROID)
                .exchange();

        assertThat(signedIn).hasStatus(302);
        UUID userId = jdbc.sql(
                        "select user_id from auth_identities where provider = 'google' and subject = 'audit-login'")
                .query(UUID.class)
                .single();
        List<Row> rows = rowsOf(userId);
        assertThat(rows).hasSize(1);
        Row row = rows.getFirst();
        assertThat(row.action()).isEqualTo("auth.signed_in");
        assertThat(row.targetType()).isEqualTo("USER");
        assertThat(row.targetId()).isEqualTo(userId);
        assertThat(row.actorUserId()).isEqualTo(userId);
        assertThat(row.workspaceId()).isNull();
        assertThat(row.metadata())
                .containsOnly(
                        Map.entry("method", "GOOGLE"), Map.entry("browser", "Chrome"), Map.entry("os", "Android"));
        // No address anywhere: not a column, not a key.
        assertThat(jdbc.sql("""
                                select column_name from information_schema.columns
                                where table_name = 'audit_events'""").query(String.class).list())
                .containsExactlyInAnyOrder(
                        "id",
                        "workspace_id",
                        "actor_user_id",
                        "action",
                        "target_type",
                        "target_id",
                        "metadata",
                        "occurred_at");
    }

    @Test
    void roleChangeIsAudited() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);

        assertThat(patchRole(ana, ws, biaMembership, "VIEWER")).hasStatusOk();

        List<Row> rows = rowsOf(ws);
        assertThat(rows).hasSize(1);
        Row row = rows.getFirst();
        assertThat(row.action()).isEqualTo("member.role_changed");
        assertThat(row.actorUserId()).isEqualTo(ana);
        assertThat(row.workspaceId()).isEqualTo(ws);
        assertThat(row.targetType()).isEqualTo("MEMBERSHIP");
        assertThat(row.targetId()).isEqualTo(biaMembership);
        assertThat(row.metadata()).containsOnly(Map.entry("from", "ADULT"), Map.entry("to", "VIEWER"));
    }

    @Test
    void everyOtherActionIsAudited() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);

        assertThat(rename(ana, ws, "Os Silva")).hasStatusOk();
        MvcTestResult invited = createInvitation(ana, ws, "Vovo");
        assertThat(invited).hasStatus(201);
        UUID invitationId = UUID.fromString(JsonPath.read(body(invited), "$.id"));
        assertThat(revoke(ana, ws, invitationId)).hasStatus(204);
        assertThat(toggle(ana, ws, "planning", true)).hasStatusOk();
        MvcTestResult recovery = recover(ana, ws, leoMembership);
        assertThat(recovery).hasStatus(201);
        UUID recoveryId = UUID.fromString(JsonPath.read(body(recovery), "$.id"));
        assertThat(accept(JsonPath.read(body(recovery), "$.token"), null)).hasStatusOk();
        assertThat(remove(ana, ws, biaMembership)).hasStatus(204);

        List<Row> rows = rowsOf(ws);
        assertThat(rows)
                .extracting(Row::action, Row::targetType, Row::targetId, Row::actorUserId, Row::metadata)
                .containsExactlyInAnyOrder(
                        tuple("workspace.renamed", "WORKSPACE", ws, ana, Map.of()),
                        tuple("invitation.created", "INVITATION", invitationId, ana, Map.of("role", "VIEWER")),
                        tuple("invitation.revoked", "INVITATION", invitationId, ana, Map.of()),
                        tuple("module.toggled", "MODULE", null, ana, Map.of("key", "planning", "enabled", "true")),
                        tuple("recovery.created", "MEMBERSHIP", leoMembership, ana, Map.of()),
                        tuple("recovery.used", "MEMBERSHIP", leoMembership, leo, Map.of()),
                        tuple("member.removed", "MEMBERSHIP", biaMembership, ana, Map.of("reason", "BY_OWNER")));
        // A recovery link is recorded once, as recovery.created on the member, never as an invitation.
        assertThat(rows).extracting(Row::targetId).doesNotContain(recoveryId);
    }

    @Test
    void joiningThroughAnInvitationIsAudited() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        MvcTestResult invited = createInvitation(ana, ws, "Vovo");
        String token = JsonPath.read(body(invited), "$.token");

        MvcTestResult accepted = accept(token, "Vovo Teste");

        assertThat(accepted).hasStatusOk();
        UUID membershipId = UUID.fromString(JsonPath.read(body(accepted), "$.membershipId"));
        UUID newUser = memberships.all(ws).stream()
                .filter(member -> member.membershipId().equals(membershipId))
                .findFirst()
                .orElseThrow()
                .userId();
        assertThat(rowsOf(ws))
                .filteredOn(row -> row.action().equals("member.joined"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.actorUserId()).isEqualTo(newUser);
                    assertThat(row.targetType()).isEqualTo("MEMBERSHIP");
                    assertThat(row.targetId()).isEqualTo(membershipId);
                    assertThat(row.metadata()).containsOnly(Map.entry("role", "VIEWER"));
                });
        // The accept signs the new member in once, by invitation: the event comes after the commit.
        assertThat(rowsOf(newUser))
                .filteredOn(row -> row.action().equals("auth.signed_in"))
                .singleElement()
                .satisfies(row -> assertThat(row.metadata()).containsEntry("method", "INVITATION"));
    }

    @Test
    void removingByAccountDeletionKeepsNoActor() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);

        memberships.detachUser(bia);

        assertThat(rowsOf(ws)).singleElement().satisfies(row -> {
            assertThat(row.action()).isEqualTo("member.removed");
            assertThat(row.actorUserId()).isNull();
            assertThat(row.targetId()).isEqualTo(biaMembership);
            assertThat(row.metadata()).containsOnly(Map.entry("reason", "ACCOUNT_DELETED"));
        });
    }

    @Test
    void metadataNeverHasNames() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);
        jdbc.sql("update memberships set nickname = 'Aninha' where workspace_id = :ws and user_id = :ana")
                .param("ws", ws)
                .param("ana", ana)
                .update();

        MvcTestResult invited = createInvitation(ana, ws, "Vovo Zelia");
        assertThat(invited).hasStatus(201);
        assertThat(rename(ana, ws, "Os Silva")).hasStatusOk();
        assertThat(patchNickname(ana, ws, biaMembership, "Bibi Mimosa")).hasStatusOk();
        assertThat(accept(JsonPath.read(body(invited), "$.token"), "Zelia Teste"))
                .hasStatusOk();

        List<Row> rows = allRows();
        assertThat(rows).hasSizeGreaterThanOrEqualTo(3);
        assertThat(rows).extracting(Row::action).contains("auth.signed_in", "invitation.created", "workspace.renamed");
        for (Row row : rows) {
            assertThat(row.metadata().toString())
                    .doesNotContain(
                            "Aninha",
                            "Vovo",
                            "Zelia",
                            "Os Silva",
                            "Bibi",
                            "Mimosa",
                            "Família Teste",
                            "Ana Teste",
                            "Bia Teste");
        }
    }

    @Test
    void failedActionLeavesNoRow() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID anaMembership = workspaces.membershipOf(ws, ana);

        MvcTestResult refused = patchRole(ana, ws, anaMembership, "ADULT");

        assertThat(refused).hasStatus(422);
        assertThat(refused).bodyJson().extractingPath("$.code").isEqualTo("member.last-owner");
        assertThat(rowsOf(ws)).isEmpty();
    }

    @Test
    void auditRowRollsBackWithItsAction() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);

        failAfterAudit.arm();
        MvcTestResult failed = patchRole(ana, ws, biaMembership, "VIEWER");

        assertThat(failed.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        assertThat(failAfterAudit.auditRowsSeen())
                .as("audit row visible before the failure")
                .isEqualTo(1);
        assertThat(rowsOf(ws)).isEmpty();
        assertThat(jdbc.sql("select role from memberships where id = :id")
                        .param("id", biaMembership)
                        .query(String.class)
                        .single())
                .isEqualTo("ADULT");
    }

    record Row(
            String action,
            String targetType,
            @Nullable UUID targetId,
            @Nullable UUID actorUserId,
            @Nullable UUID workspaceId,
            Map<String, Object> metadata) {}

    /** The audit rows of a family, or of a person as actor, oldest first. */
    private List<Row> rowsOf(UUID workspaceOrUser) {
        return jdbc.sql("""
                        select action, target_type, target_id, actor_user_id, workspace_id, metadata::text as metadata
                        from audit_events
                        where workspace_id = :id or actor_user_id = :id
                        order by occurred_at, id""")
                .param("id", workspaceOrUser)
                .query((rs, row) -> new Row(
                        rs.getString("action"),
                        rs.getString("target_type"),
                        rs.getObject("target_id", UUID.class),
                        rs.getObject("actor_user_id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        JsonPath.<Map<String, Object>>read(rs.getString("metadata"), "$")))
                .list();
    }

    /** Every audit row of the table, whoever it belongs to. */
    private List<Row> allRows() {
        return jdbc.sql("""
                        select action, target_type, target_id, actor_user_id, workspace_id, metadata::text as metadata
                        from audit_events
                        order by occurred_at, id""")
                .query((rs, row) -> new Row(
                        rs.getString("action"),
                        rs.getString("target_type"),
                        rs.getObject("target_id", UUID.class),
                        rs.getObject("actor_user_id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        JsonPath.<Map<String, Object>>read(rs.getString("metadata"), "$")))
                .list();
    }

    private RequestPostProcessor session(UUID userId) {
        return TestUsers.signedIn(userId, null);
    }

    private MvcTestResult patchRole(UUID caller, UUID ws, UUID membershipId, String role) {
        return patch(caller, "/v1/workspaces/" + ws + "/members/" + membershipId, "{\"role\":\"" + role + "\"}");
    }

    private MvcTestResult patchNickname(UUID caller, UUID ws, UUID membershipId, String nickname) {
        return patch(
                caller, "/v1/workspaces/" + ws + "/members/" + membershipId, "{\"nickname\":\"" + nickname + "\"}");
    }

    private MvcTestResult rename(UUID caller, UUID ws, String name) {
        return patch(caller, "/v1/workspaces/" + ws, "{\"name\":\"" + name + "\"}");
    }

    private MvcTestResult patch(UUID caller, String uri, String body) {
        return mvc.patch()
                .uri(uri)
                .with(session(caller))
                .with(CsrfSession.start(mvc, session(caller)))
                .contentType(JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult toggle(UUID caller, UUID ws, String key, boolean enabled) {
        return mvc.put()
                .uri("/v1/workspaces/" + ws + "/modules/" + key)
                .with(session(caller))
                .with(CsrfSession.start(mvc, session(caller)))
                .contentType(JSON)
                .content("{\"enabled\":" + enabled + "}")
                .exchange();
    }

    private MvcTestResult createInvitation(UUID caller, UUID ws, String nickname) {
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/invitations")
                .with(session(caller))
                .with(CsrfSession.start(mvc, session(caller)))
                .contentType(JSON)
                .content("{\"role\":\"VIEWER\",\"nickname\":\"" + nickname + "\"}")
                .exchange();
    }

    private MvcTestResult revoke(UUID caller, UUID ws, UUID invitationId) {
        return mvc.delete()
                .uri("/v1/workspaces/" + ws + "/invitations/" + invitationId)
                .with(session(caller))
                .with(CsrfSession.start(mvc, session(caller)))
                .exchange();
    }

    private MvcTestResult recover(UUID caller, UUID ws, UUID membershipId) {
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/members/" + membershipId + "/recovery")
                .with(session(caller))
                .with(CsrfSession.start(mvc, session(caller)))
                .exchange();
    }

    private MvcTestResult remove(UUID caller, UUID ws, UUID membershipId) {
        return mvc.delete()
                .uri("/v1/workspaces/" + ws + "/members/" + membershipId)
                .with(session(caller))
                .with(CsrfSession.start(mvc, session(caller)))
                .exchange();
    }

    private MvcTestResult accept(String token, @Nullable String displayName) {
        String body = displayName == null
                ? "{\"token\":\"" + token + "\"}"
                : "{\"token\":\"" + token + "\",\"newUser\":{\"displayName\":\"" + displayName + "\"}}";
        String address = "10.9.0." + NEXT_ADDRESS.incrementAndGet();
        return mvc.post()
                .uri("/v1/invitations/accept")
                .with(CsrfSession.start(mvc))
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .contentType(JSON)
                .content(body)
                .exchange();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** What the browser does on the provider's page: sign in as {@code username} and follow it back to the API. */
    private static String signInAtProvider(String authorizeUrl, String username) throws Exception {
        String claims = "{\"name\":\"Ana Teste\",\"email\":\"" + username.replace('-', '.')
                + "@example.com\",\"family_name\":\"Teste\"}";
        String form = "username=" + username + "&claims=" + URLEncoder.encode(claims, StandardCharsets.UTF_8);
        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<Void> response = http.send(
                    HttpRequest.newBuilder(URI.create(authorizeUrl))
                            .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(form))
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(302);
            return response.headers().firstValue(HttpHeaders.LOCATION).orElseThrow();
        }
    }
}
