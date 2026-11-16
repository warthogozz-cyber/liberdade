package br.com.liberdadefamilia.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.identity.internal.SessionUserAuthentication;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestWorkspaces;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * A whole sign-in against the mock provider from compose.e2e.yaml: the code exchange, the ID token check, the user info
 * call and the session that comes out of it. Its own context, since the provider's address is only known at runtime.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SignInIT {

    private static final String WEB_ORIGIN = "http://localhost:5173";
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
    TestWorkspaces workspaces;

    @Test
    void signInRaisesTheSessionTo30DaysIdle() throws Exception {
        var start = mvc.get().uri("/oauth2/authorization/google").exchange();
        assertThat(start).hasStatus(302);
        Cookie anonymous = start.getResponse().getCookie(SESSION_COOKIE);
        assertThat(anonymous).isNotNull();
        assertThat(sessionRow(sessionIdOf(anonymous))).containsEntry("max_inactive_interval", 1_800);

        String callback = signInAtProvider(start.getResponse().getHeader(HttpHeaders.LOCATION), "ana-login");
        var signedIn = mvc.get().uri(URI.create(callback)).cookie(anonymous).exchange();

        assertThat(signedIn).hasStatus(302).hasRedirectedUrl(WEB_ORIGIN + "/");
        Cookie session = signedIn.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        // A new session id on sign-in, so the 30 minutes and the 30 days are two different rows.
        assertThat(session.getValue()).isNotEqualTo(anonymous.getValue());
        UUID userId = jdbc.sql(
                        "select user_id from auth_identities where provider = 'google' and subject = 'ana-login'")
                .query(UUID.class)
                .single();
        assertThat(sessionRow(sessionIdOf(session)))
                .containsEntry("max_inactive_interval", 2_592_000)
                .containsEntry("principal_name", userId.toString());
        assertThat(mvc.get().uri("/v1/csrf").cookie(session)).hasStatusOk();
    }

    @Test
    void storedContextIsMinimal() throws Exception {
        Cookie session = signedInSession(signIn("ana-login"));
        UUID userId = userIdOf("ana-login");

        byte[] stored = sessionAttribute(sessionIdOf(session), "SPRING_SECURITY_CONTEXT");

        Object context;
        try (var in = new ObjectInputStream(new ByteArrayInputStream(stored))) {
            context = in.readObject();
        }
        assertThat(context)
                .isInstanceOfSatisfying(
                        SecurityContext.class,
                        restored -> assertThat(restored.getAuthentication())
                                .isInstanceOfSatisfying(
                                        SessionUserAuthentication.class,
                                        authentication -> assertThat(authentication.getName())
                                                .isEqualTo(userId.toString())));
        String bytes = new String(stored, StandardCharsets.ISO_8859_1);
        assertThat(bytes).doesNotContain("ana.login@example.com", "Ana Teste", "id_token", "OidcIdToken");
    }

    @Test
    void signInCookieIsPersistent() throws Exception {
        var signedIn = signIn("ana-login");

        assertThat(sessionSetCookie(signedIn)).contains("Max-Age=2592000");
    }

    @Test
    void familyNameHintSurvivesUntilFamilyExists() throws Exception {
        Cookie session = signedInSession(signIn("bia-login"));
        RequestPostProcessor withSession = request -> {
            request.setCookies(session);
            return request;
        };

        assertThat(mvc.get().uri("/v1/me").with(withSession))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.suggestedWorkspaceName")
                .isEqualTo("Família Teste");
        var created = mvc.post()
                .uri("/v1/workspaces")
                .with(CsrfSession.start(mvc, withSession))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Família Teste\"}")
                .exchange();

        assertThat(created).hasStatus(201);
        assertThat(hintCount(sessionIdOf(session))).isZero();
    }

    /** A returning user already has a family: the first look at /v1/me drops Google's family name from the session. */
    @Test
    void returningUserHintIsForgottenOnMe() throws Exception {
        signIn("cris-login");
        workspaces.createFor(userIdOf("cris-login"));
        Cookie session = signedInSession(signIn("cris-login"));
        assertThat(hintCount(sessionIdOf(session))).isOne();

        assertThat(mvc.get().uri("/v1/me").cookie(session))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.suggestedWorkspaceName")
                .isEqualTo("Minha família");
        assertThat(hintCount(sessionIdOf(session))).isZero();
    }

    /** The whole trip: to the provider and back, signed in as {@code username}. Answers the callback's response. */
    private MvcTestResult signIn(String username) throws Exception {
        var start = mvc.get().uri("/oauth2/authorization/google").exchange();
        assertThat(start).hasStatus(302);
        Cookie anonymous = start.getResponse().getCookie(SESSION_COOKIE);
        assertThat(anonymous).isNotNull();
        String callback = signInAtProvider(start.getResponse().getHeader(HttpHeaders.LOCATION), username);
        var signedIn = mvc.get().uri(URI.create(callback)).cookie(anonymous).exchange();
        assertThat(signedIn).hasStatus(302).hasRedirectedUrl(WEB_ORIGIN + "/");
        return signedIn;
    }

    private static Cookie signedInSession(MvcTestResult signedIn) {
        Cookie session = signedIn.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    private static String sessionSetCookie(MvcTestResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(SESSION_COOKIE + "="))
                .findFirst()
                .orElseThrow();
    }

    private UUID userIdOf(String subject) {
        return jdbc.sql("select user_id from auth_identities where provider = 'google' and subject = :subject")
                .param("subject", subject)
                .query(UUID.class)
                .single();
    }

    private long hintCount(String sessionId) {
        return jdbc.sql("""
                        select count(*) from spring_session_attributes a
                        join spring_session s on s.primary_id = a.session_primary_id
                        where s.session_id = :id and a.attribute_name = 'liberdade.familyNameHint'""").param("id", sessionId).query(Long.class).single();
    }

    private byte[] sessionAttribute(String sessionId, String name) {
        return jdbc.sql("""
                        select a.attribute_bytes from spring_session_attributes a
                        join spring_session s on s.primary_id = a.session_primary_id
                        where s.session_id = :id and a.attribute_name = :name""")
                .param("id", sessionId)
                .param("name", name)
                .query(byte[].class)
                .single();
    }

    /**
     * What the browser does on the provider's page: sign in as {@code username}, with an e-mail, a name and a family
     * name, and follow it back to the API.
     */
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

    private Map<String, Object> sessionRow(String sessionId) {
        return jdbc.sql("select max_inactive_interval, principal_name from spring_session where session_id = :id")
                .param("id", sessionId)
                .query()
                .singleRow();
    }

    /** Spring Session writes the session id Base64-encoded in the cookie. */
    private static String sessionIdOf(Cookie cookie) {
        return new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
    }
}
