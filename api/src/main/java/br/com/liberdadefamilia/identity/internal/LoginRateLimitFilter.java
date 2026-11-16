package br.com.liberdadefamilia.identity.internal;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import br.com.liberdadefamilia.shared.RateLimitedException;
import br.com.liberdadefamilia.shared.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Applies the {@link RateLimiter} to the sign-in endpoints, keyed by the client address (D26). Each request to
 * them counts one attempt. Over the limit, a page the browser navigated to goes back to the sign-in screen, and a
 * request from the web app's code gets a 429 problem, whose {@code Retry-After} the problem handler sets.
 */
final class LoginRateLimitFilter extends OncePerRequestFilter {

    /**
     * Only the start of a Google sign-in counts. The callback is left out (FR-M5-limiter): {@code state} and PKCE
     * already protect it, and counting it made each sign-in cost two attempts.
     */
    private static final RequestMatcher NAVIGATION = pathPattern("/oauth2/authorization/**");

    private static final RequestMatcher XHR =
            new OrRequestMatcher(pathPattern("/webauthn/authenticate/options"), pathPattern("/login/webauthn"));

    private final RateLimiter limiter;
    private final HandlerExceptionResolver resolver;
    private final String limitedPage;

    LoginRateLimitFilter(RateLimiter limiter, HandlerExceptionResolver resolver, IdentityProperties properties) {
        this.limiter = limiter;
        this.resolver = resolver;
        this.limitedPage = properties.web().origin() + "/entrar?erro=limite";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !NAVIGATION.matches(request) && !XHR.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String address = request.getRemoteAddr();
        if (limiter.tryAcquire(address)) {
            chain.doFilter(request, response);
        } else if (NAVIGATION.matches(request)) {
            response.sendRedirect(limitedPage);
        } else {
            var limited = new RateLimitedException(limiter.retryAfterSeconds(address));
            if (resolver.resolveException(request, response, null, limited) == null) {
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value());
            }
        }
    }
}
