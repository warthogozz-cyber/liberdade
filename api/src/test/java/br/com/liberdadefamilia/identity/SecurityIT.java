package br.com.liberdadefamilia.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityIT {

    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");
    private static final String WEB_ORIGIN = "http://localhost:5173";
    private static final String SESSION_COOKIE = CsrfSession.SESSION_COOKIE;
    private static final String AUTHORIZATION_REQUEST =
            HttpSessionOAuth2AuthorizationRequestRepository.class.getName() + ".AUTHORIZATION_REQUEST";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Test
    void meRequiresSignIn() {
        var result = mvc.get().uri("/v1/me").exchange();
        assertThat(result).hasStatus(401).hasContentType(PROBLEM_JSON);
        assertThat(result).headers().doesNotContainHeader(HttpHeaders.LOCATION);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.required");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Entre para continuar.");
    }

    @Test
    void noGeneratedLoginPage() {
        assertThat(mvc.get().uri("/login")).hasStatus(401);
    }

    @Test
    void googleRedirectUsesPkceNonceAndMinimalScopes() {
        var result = mvc.get().uri("/oauth2/authorization/google").exchange();
        assertThat(result).hasStatus(302);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth")
                .contains("scope=openid%20email%20profile", "code_challenge_method=S256", "code_challenge=", "nonce=");
    }

    @Test
    void failedCallbackGoesBackToSignIn() {
        assertThat(mvc.get().uri("/login/oauth2/code/google?error=access_denied&state=x"))
                .hasStatus(302)
                .hasRedirectedUrl(WEB_ORIGIN + "/entrar?erro=login");
    }

    @Test
    void cancellingAtGoogleGoesBackToSignIn() {
        var start = mvc.get().uri("/oauth2/authorization/google").exchange();
        Cookie session = start.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        String state = UriComponentsBuilder.fromUriString(start.getResponse().getHeader(HttpHeaders.LOCATION))
                .build()
                .getQueryParams()
                .getFirst("state");
        assertThat(state).isNotBlank();
        assertThat(authorizationRequestsIn(sessionIdOf(start))).isOne();

        // What Google sends back when the person cancels on its consent screen.
        var cancelled = mvc.get()
                .uri("/login/oauth2/code/google")
                .param("error", "access_denied")
                .param("state", UriUtils.decode(state, StandardCharsets.UTF_8))
                .cookie(session)
                .exchange();

        assertThat(cancelled).hasStatus(302).hasRedirectedUrl(WEB_ORIGIN + "/entrar?erro=login");
        // The state matched: the pending authorization request was used up, not left behind as with a stray callback.
        assertThat(authorizationRequestsIn(sessionIdOf(start))).isZero();
    }

    @Test
    void csrfEndpointGivesToken() {
        var result = mvc.get().uri("/v1/csrf").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.headerName").isEqualTo("X-XSRF-TOKEN");
        assertThat(result).bodyJson().extractingPath("$.token").asString().isNotBlank();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith(SESSION_COOKIE + "=")
                .contains("Path=/", "Secure", "HttpOnly", "SameSite=Lax");
    }

    @Test
    void anonymousSessionLasts30MinutesIdle() {
        String sessionId = sessionIdOf(mvc.get().uri("/v1/csrf").exchange());
        Integer maxInactiveInterval = jdbc.sql(
                        "select max_inactive_interval from spring_session where session_id = :id")
                .param("id", sessionId)
                .query(Integer.class)
                .single();
        assertThat(maxInactiveInterval).isEqualTo(1_800);
    }

    @Test
    void anonymousCookieHasNoMaxAge() {
        var result = mvc.get().uri("/v1/csrf").exchange();

        assertThat(sessionSetCookie(result)).doesNotContain("Max-Age");
    }

    @Test
    void postWithoutCsrfIs403() {
        var result = mvc.post().uri("/logout").exchange();
        assertThat(result).hasStatus(403).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("csrf.invalid");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Sua sessão foi atualizada. Tente de novo.");
    }

    @Test
    void logoutIs204() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        var csrf = CsrfSession.start(mvc, TestUsers.signedIn(userId, "Teste"));

        assertThat(mvc.post()
                        .uri("/logout")
                        .with(TestUsers.signedIn(userId, "Teste"))
                        .with(csrf))
                .hasStatus(204);
    }

    @Test
    void logoutExpiresCookie() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        var csrf = CsrfSession.start(mvc, TestUsers.signedIn(userId, null));

        var logout = mvc.post()
                .uri("/logout")
                .with(TestUsers.signedIn(userId, null))
                .with(csrf)
                .exchange();

        assertThat(logout).hasStatus(204);
        assertThat(sessionSetCookie(logout)).startsWith(SESSION_COOKIE + "=;").contains("Max-Age=0");
    }

    @Test
    void tokenFromCsrfEndpointIsAcceptedInItsHeader() {
        var csrf = CsrfSession.start(mvc);

        var logout = mvc.post().uri("/logout").with(csrf).exchange();

        assertThat(logout).hasStatus(204);
        Long sessions = jdbc.sql("select count(*) from spring_session where session_id = :id")
                .param("id", csrf.sessionId())
                .query(Long.class)
                .single();
        assertThat(sessions).isZero();
    }

    @Test
    void corsAllowsOnlyWebOrigin() {
        var allowed = mvc.options()
                .uri("/v1/me")
                .header(HttpHeaders.ORIGIN, WEB_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange();
        assertThat(allowed).hasStatusOk();
        assertThat(allowed)
                .headers()
                .hasValue(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB_ORIGIN)
                .hasValue(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");

        assertThat(mvc.options()
                        .uri("/v1/me")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .hasStatus(403);
    }

    /** The web edits with PATCH and deletes with DELETE, so the preflight for both must pass. */
    @Test
    void corsAllowsPatchAndDelete() {
        for (String method : List.of("PATCH", "DELETE")) {
            var preflight = mvc.options()
                    .uri("/v1/me")
                    .header(HttpHeaders.ORIGIN, WEB_ORIGIN)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                    .exchange();

            assertThat(preflight).as(method).hasStatusOk();
            assertThat(preflight.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
                    .as(method)
                    .contains(method);
        }
    }

    @Test
    void securityHeaders() {
        var result = mvc.get().uri("/actuator/health").secure(true).exchange();
        assertThat(result).hasStatusOk();
        assertThat(result)
                .headers()
                .hasValue("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
                .hasValue("Referrer-Policy", "no-referrer")
                .hasValue("X-Content-Type-Options", "nosniff")
                .hasValue("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        assertThat(result.getResponse().getHeader("Strict-Transport-Security")).contains("max-age=31536000");
    }

    private Long authorizationRequestsIn(String sessionId) {
        return jdbc.sql("""
                        select count(*) from spring_session_attributes a
                        join spring_session s on s.primary_id = a.session_primary_id
                        where s.session_id = :id and a.attribute_name = :name""")
                .param("id", sessionId)
                .param("name", AUTHORIZATION_REQUEST)
                .query(Long.class)
                .single();
    }

    private static String sessionSetCookie(MvcTestResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(SESSION_COOKIE + "="))
                .findFirst()
                .orElseThrow();
    }

    private static String sessionIdOf(MvcTestResult result) {
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(cookie).isNotNull();
        return CsrfSession.sessionIdOf(cookie);
    }
}
