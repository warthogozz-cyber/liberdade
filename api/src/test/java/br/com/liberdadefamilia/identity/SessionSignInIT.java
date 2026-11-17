package br.com.liberdadefamilia.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Signing in through a link starts a clean session: the id changes, what the anonymous session held is gone, and the
 * CSRF token it handed out stops working.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SessionSignInIT.Config.class})
class SessionSignInIT {

    private static final String MARKER = "test.anonymousMarker";
    private static final String MARKER_HEADER = "X-Test-Marker";
    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger();

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        /** Puts a marker in the session of any request that asks for one. */
        @Bean
        OncePerRequestFilter markerFilter() {
            return new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(
                        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws ServletException, IOException {
                    if (request.getHeader(MARKER_HEADER) != null) {
                        request.getSession().setAttribute(MARKER, "anonymous");
                    }
                    chain.doFilter(request, response);
                }
            };
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

    @Test
    void inviteSignInDropsAnonymousAttributes() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        String token = newInvitation(ana, ws);
        CsrfSession anonymous = CsrfSession.start(mvc, withMarker());
        assertThat(markers(anonymous.sessionId())).isOne();

        MvcTestResult accepted = accept(token, "Vovo Teste", anonymous);

        assertThat(accepted).hasStatusOk();
        Cookie session = accepted.getResponse().getCookie(CsrfSession.SESSION_COOKIE);
        assertThat(session).isNotNull();
        assertThat(CsrfSession.sessionIdOf(session)).isNotEqualTo(anonymous.sessionId());
        assertThat(markers(CsrfSession.sessionIdOf(session))).isZero();
        assertThat(markers(anonymous.sessionId())).isZero();
        assertThat(mvc.get().uri("/v1/me").cookie(session).exchange()).hasStatusOk();
    }

    @Test
    void inviteSignInRotatesCsrf() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        String token = newInvitation(ana, ws);
        String probe = newInvitation(ana, ws);
        CsrfSession anonymous = CsrfSession.start(mvc);

        MvcTestResult accepted = accept(token, "Vovo Teste", anonymous);

        assertThat(accepted).hasStatusOk();
        assertCsrfRotated(anonymous, accepted.getResponse().getCookie(CsrfSession.SESSION_COOKIE), probe);
    }

    @Test
    void recoverySignInRotatesCsrf() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        String token = newRecovery(ana, ws, leoMembership);
        String probe = newInvitation(ana, ws);
        CsrfSession anonymous = CsrfSession.start(mvc, withMarker());

        MvcTestResult accepted = accept(token, null, anonymous);

        assertThat(accepted).hasStatusOk();
        Cookie session = accepted.getResponse().getCookie(CsrfSession.SESSION_COOKIE);
        assertThat(markers(CsrfSession.sessionIdOf(session))).isZero();
        assertCsrfRotated(anonymous, session, probe);
    }

    /** The old token fails with the new session, and the one {@code GET /v1/csrf} hands out next works. */
    private void assertCsrfRotated(CsrfSession anonymous, Cookie session, String probeToken) {
        assertThat(session).isNotNull();
        var stale = new CsrfSession(session, anonymous.headerName(), anonymous.token());
        MvcTestResult denied = preview(probeToken, stale);
        assertThat(denied).hasStatus(403);
        assertThat(denied).bodyJson().extractingPath("$.code").isEqualTo("csrf.invalid");

        CsrfSession fresh = CsrfSession.start(mvc, request -> {
            request.setCookies(session);
            return request;
        });
        assertThat(fresh.token()).isNotEqualTo(anonymous.token());
        assertThat(preview(probeToken, fresh)).hasStatusOk();
    }

    private long markers(String sessionId) {
        return jdbc.sql("""
                        select count(*)
                        from spring_session_attributes a
                        join spring_session s on s.primary_id = a.session_primary_id
                        where s.session_id = :id and a.attribute_name = :name""")
                .param("id", sessionId)
                .param("name", MARKER)
                .query(Long.class)
                .single();
    }

    private static RequestPostProcessor withMarker() {
        return request -> {
            request.addHeader(MARKER_HEADER, "1");
            return request;
        };
    }

    private MvcTestResult accept(String token, String displayName, CsrfSession csrf) {
        String body = displayName == null
                ? "{\"token\":\"" + token + "\"}"
                : "{\"token\":\"" + token + "\",\"newUser\":{\"displayName\":\"" + displayName + "\"}}";
        return post("/v1/invitations/accept", body, csrf);
    }

    private MvcTestResult preview(String token, CsrfSession csrf) {
        return post("/v1/invitations/preview", "{\"token\":\"" + token + "\"}", csrf);
    }

    private MvcTestResult post(String uri, String body, CsrfSession csrf) {
        return mvc.post()
                .uri(uri)
                .with(csrf)
                .with(request -> {
                    int n = NEXT_ADDRESS.incrementAndGet();
                    request.setRemoteAddr("10.4." + (n / 250) + "." + (n % 250 + 1));
                    return request;
                })
                .contentType(JSON)
                .content(body)
                .exchange();
    }

    private String newInvitation(UUID ownerId, UUID ws) {
        RequestPostProcessor owner = TestUsers.signedIn(ownerId, null);
        MvcTestResult created = mvc.post()
                .uri("/v1/workspaces/" + ws + "/invitations")
                .with(owner)
                .with(CsrfSession.start(mvc, owner))
                .contentType(JSON)
                .content("{\"role\":\"VIEWER\",\"nickname\":\"Vovo\"}")
                .exchange();
        assertThat(created).hasStatus(201);
        return JsonPath.read(body(created), "$.token");
    }

    private String newRecovery(UUID ownerId, UUID ws, UUID membershipId) {
        RequestPostProcessor owner = TestUsers.signedIn(ownerId, null);
        MvcTestResult created = mvc.post()
                .uri("/v1/workspaces/" + ws + "/members/" + membershipId + "/recovery")
                .with(owner)
                .with(CsrfSession.start(mvc, owner))
                .exchange();
        assertThat(created).hasStatus(201);
        return JsonPath.read(body(created), "$.token");
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
