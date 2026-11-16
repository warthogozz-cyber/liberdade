package br.com.liberdadefamilia.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
 * Asking for the account to be deleted: 30 days of grace in which the person can still sign in, look at their data,
 * export it and cancel, and nothing else. Has the mock sign-in provider, for the one test that signs in again.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountDeletionIT {

    private static final String SESSION_COOKIE = "__Host-sid";

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

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestLedger ledger;

    @Test
    void adultSchedulesDeletion() {
        Family f = family();

        MvcTestResult result = request(f.bia, 0);

        assertThat(result).hasStatus(202);
        Instant scheduled = Instant.parse(JsonPath.read(body(result), "$.deletionScheduledFor"));
        assertThat(scheduled)
                .isBetween(
                        Instant.now().plus(Duration.ofDays(30)).minusSeconds(60),
                        Instant.now().plus(Duration.ofDays(30)));
        assertThat(get(f.bia, "/v1/me"))
                .bodyJson()
                .extractingPath("$.user.deletionScheduledFor")
                .isEqualTo(JsonPath.read(body(result), "$.deletionScheduledFor"));
    }

    @Test
    void repeatingTheRequestKeepsTheDate() {
        Family f = family();

        MvcTestResult first = request(f.bia, 0);
        MvcTestResult second = request(f.bia, 0);

        assertThat(second).hasStatus(202);
        assertThat((String) JsonPath.read(body(second), "$.deletionScheduledFor"))
                .isEqualTo(JsonPath.read(body(first), "$.deletionScheduledFor"));
        assertThat(actions(f.bia)).containsExactly("account.deletion_requested");
    }

    @Test
    void deletionNeedsRecentSignIn() {
        Family f = family();

        MvcTestResult result = request(f.bia, 11);

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.reauthentication-required");
    }

    @Test
    void soleOwnerMustTransferFirst() {
        Family f = family();

        MvcTestResult refused = request(f.ana, 0);

        assertThat(refused).hasStatus(409);
        assertThat(refused).bodyJson().extractingPath("$.code").isEqualTo("account.sole-owner");
        jdbc.sql("update memberships set role = 'OWNER' where workspace_id = :ws and user_id = :bia")
                .param("ws", f.ws)
                .param("bia", f.bia)
                .update();
        assertThat(request(f.ana, 0)).hasStatus(202);
    }

    @Test
    void soleOwnerOfPendingFamilyMayDelete() {
        Family f = family();
        jdbc.sql("update workspaces set deletion_requested_at = now() where id = :ws")
                .param("ws", f.ws)
                .update();

        assertThat(request(f.ana, 0)).hasStatus(202);
    }

    @Test
    void pendingAccountIsLockedDown() {
        Family f = family();
        UUID category = ledger.categoryOf(f.ws, "EXPENSE", "Mercado");
        assertThat(get(f.bia, "/v1/workspaces/" + f.ws + "/accounts")).hasStatusOk();
        assertThat(category).isNotNull();
        request(f.bia, 0);

        MvcTestResult locked = get(f.bia, "/v1/workspaces/" + f.ws + "/accounts");

        assertThat(locked).hasStatus(403);
        assertThat(locked).bodyJson().extractingPath("$.code").isEqualTo("account.deletion-pending");
        assertThat(locked).bodyJson().extractingPath("$.detail").isEqualTo("Sua conta está marcada para exclusão.");
        assertThat(get(f.bia, "/v1/me")).hasStatusOk();
        assertThat(get(f.bia, "/v1/csrf")).hasStatusOk();
        assertThat(post(f.bia, "/v1/me/export", 0)).hasStatusOk();
        assertThat(post(f.bia, "/logout", 0).getResponse().getStatus()).isLessThan(400);
        assertThat(post(f.bia, "/webauthn/register", 0).getResponse().getStatus())
                .isEqualTo(403);
    }

    @Test
    void anonymousRequestsAreUntouched() {
        assertThat(mvc.get().uri("/v1/csrf").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/v1/me").exchange()).hasStatus(401);
    }

    @Test
    void pendingAccountCanSignIn() throws Exception {
        MvcTestResult first = signIn("deleting-login");
        UUID userId = jdbc.sql(
                        "select user_id from auth_identities where provider = 'google' and subject = 'deleting-login'")
                .query(UUID.class)
                .single();
        Cookie firstSession = first.getResponse().getCookie(SESSION_COOKIE);
        assertThat(firstSession).isNotNull();
        assertThat(mvc.post()
                        .uri("/v1/me/deletion")
                        .cookie(firstSession)
                        .with(CsrfSession.start(mvc, cookieOf(firstSession)))
                        .exchange())
                .hasStatus(202);

        MvcTestResult again = signIn("deleting-login");

        Cookie session = again.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        assertThat(jdbc.sql("select count(*) from spring_session where principal_name = :id")
                        .param("id", userId.toString())
                        .query(Long.class)
                        .single())
                .isPositive();
        assertThat(mvc.get().uri("/v1/me").cookie(session))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.user.deletionScheduledFor")
                .isNotNull();
    }

    @Test
    void cancelRestoresAccess() {
        Family f = family();
        request(f.bia, 0);

        MvcTestResult cancelled = delete(f.bia);

        assertThat(cancelled).hasStatus(204);
        assertThat(get(f.bia, "/v1/workspaces/" + f.ws + "/accounts")).hasStatusOk();
        assertThat(get(f.bia, "/v1/me"))
                .bodyJson()
                .extractingPath("$.user.deletionScheduledFor")
                .isNull();
        assertThat(delete(f.bia)).hasStatus(204);
    }

    @Test
    void deletionIsAudited() {
        Family f = family();

        request(f.bia, 0);
        delete(f.bia);
        delete(f.bia);

        assertThat(actions(f.bia)).containsExactly("account.deletion_requested", "account.deletion_cancelled");
        var row = jdbc.sql("""
                        select workspace_id, target_type, target_id, metadata::text as metadata
                        from audit_events where action = 'account.deletion_requested' and actor_user_id = :bia""").param("bia", f.bia).query().singleRow();
        assertThat(row.get("workspace_id")).isNull();
        assertThat(row).containsEntry("target_type", "USER").containsEntry("target_id", f.bia);
        assertThat(row.get("metadata").toString()).isEqualTo("{}");
    }

    // --- helpers ---

    private record Family(UUID ana, UUID bia, UUID ws) {}

    /** Ana is the only Owner of "Família Teste"; Bia is an adult in it. */
    private Family family() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
        return new Family(ana, bia, ws);
    }

    private MvcTestResult request(UUID user, int minutesAgo) {
        return post(user, "/v1/me/deletion", minutesAgo);
    }

    private MvcTestResult post(UUID user, String uri, int minutesAgo) {
        RequestPostProcessor session = session(user, minutesAgo);
        return mvc.post()
                .uri(uri)
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();
    }

    private MvcTestResult delete(UUID user) {
        RequestPostProcessor session = session(user, 0);
        return mvc.delete()
                .uri("/v1/me/deletion")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();
    }

    private MvcTestResult get(UUID user, String uri) {
        return mvc.get().uri(uri).with(session(user, 0)).exchange();
    }

    private static RequestPostProcessor session(UUID user, int minutesAgo) {
        return minutesAgo == 0 ? TestUsers.signedIn(user, null) : TestUsers.signedInMinutesAgo(user, minutesAgo);
    }

    private static RequestPostProcessor cookieOf(Cookie cookie) {
        return request -> {
            request.setCookies(cookie);
            return request;
        };
    }

    private List<String> actions(UUID user) {
        return jdbc.sql("""
                        select action from audit_events
                        where actor_user_id = :user and action like 'account.deletion_%'
                        order by occurred_at, id""").param("user", user).query(String.class).list();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The whole trip to the mock provider and back, signed in as {@code username}. Answers the callback's response. */
    private MvcTestResult signIn(String username) throws Exception {
        var start = mvc.get().uri("/oauth2/authorization/google").exchange();
        assertThat(start).hasStatus(302);
        Cookie anonymous = start.getResponse().getCookie(SESSION_COOKIE);
        assertThat(anonymous).isNotNull();
        String callback = signInAtProvider(start.getResponse().getHeader(HttpHeaders.LOCATION), username);
        var signedIn = mvc.get().uri(URI.create(callback)).cookie(anonymous).exchange();
        assertThat(signedIn).hasStatus(302);
        return signedIn;
    }

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
