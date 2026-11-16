package br.com.liberdadefamilia.identity.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rewrites the signed-in session cookie with a fresh 30-day Max-Age at most once a day, so the cookie expires 30 days
 * after the last use, like the session itself, rather than 30 days after the sign-in. Spring Session writes the cookie
 * only when the session id changes. An anonymous session never gets a Max-Age.
 */
class SessionCookieRefreshFilter extends OncePerRequestFilter {

    /** Session attribute: the {@link Instant} the cookie was last written with a Max-Age. */
    static final String REFRESHED_AT = "liberdade.cookieRefreshedAt";

    private static final Duration REFRESH_EVERY = Duration.ofHours(24);

    private final AuthenticationTrustResolver trust = new AuthenticationTrustResolverImpl();
    private final Clock clock;
    private final CookieSerializer cookies;

    SessionCookieRefreshFilter(Clock clock, CookieSerializer cookies) {
        this.clock = clock;
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && signedIn() && due(session)) {
            session.setAttribute(REFRESHED_AT, clock.instant());
            request.setAttribute(PersistentSessionCookieSerializer.PERSISTENT, Boolean.TRUE);
            // Before the chain: once a handler writes the body, the response may be committed.
            cookies.writeCookieValue(new CookieValue(request, response, session.getId()));
        }
        chain.doFilter(request, response);
    }

    private boolean signedIn() {
        return trust.isAuthenticated(SecurityContextHolder.getContext().getAuthentication());
    }

    private boolean due(HttpSession session) {
        return !(session.getAttribute(REFRESHED_AT) instanceof Instant refreshedAt)
                || refreshedAt.plus(REFRESH_EVERY).isBefore(clock.instant());
    }
}
