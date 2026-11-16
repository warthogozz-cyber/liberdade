package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionCookieRefreshFilterTest {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");
    private static final UUID USER_ID = UUID.fromString("0190a3f0-0000-7000-8000-000000000004");

    private final SessionCookieRefreshFilter filter =
            new SessionCookieRefreshFilter(Clock.fixed(NOW, ZoneOffset.UTC), new PersistentSessionCookieSerializer());
    private final MockHttpSession session = new MockHttpSession();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    SessionCookieRefreshFilterTest() {
        request.setSession(session);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn() {
        SecurityContextHolder.getContext().setAuthentication(new SessionUserAuthentication(new SessionUser(USER_ID)));
    }

    @Test
    void signedInWithoutMarkWritesPersistentCookie() throws Exception {
        signIn();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("__Host-sid=")
                .contains("Max-Age=2592000");
        assertThat(session.getAttribute(SessionCookieRefreshFilter.REFRESHED_AT))
                .isEqualTo(NOW);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void markFrom23HoursAgoWritesNothing() throws Exception {
        signIn();
        Instant mark = NOW.minus(Duration.ofHours(23));
        session.setAttribute(SessionCookieRefreshFilter.REFRESHED_AT, mark);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(session.getAttribute(SessionCookieRefreshFilter.REFRESHED_AT))
                .isEqualTo(mark);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void markFrom25HoursAgoWritesAndMovesTheMark() throws Exception {
        signIn();
        session.setAttribute(SessionCookieRefreshFilter.REFRESHED_AT, NOW.minus(Duration.ofHours(25)));

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=2592000");
        assertThat(session.getAttribute(SessionCookieRefreshFilter.REFRESHED_AT))
                .isEqualTo(NOW);
    }

    @Test
    void anonymousWritesNothing() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(session.getAttribute(SessionCookieRefreshFilter.REFRESHED_AT))
                .isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void withoutSessionWritesNothing() throws Exception {
        signIn();
        var sessionless = new MockHttpServletRequest();

        filter.doFilter(sessionless, response, chain);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(sessionless.getSession(false)).isNull();
    }
}
