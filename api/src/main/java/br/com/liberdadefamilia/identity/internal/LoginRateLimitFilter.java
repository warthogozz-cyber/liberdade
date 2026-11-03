package br.com.liberdadefamilia.identity.internal;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.OptionalLong;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Applies the {@link LoginRateLimiter} to the sign-in endpoints, keyed by the client address (D26). Each request to
 * them counts one attempt. Over the limit, a page the browser navigated to goes back to the sign-in screen, and a
 * request from the web app's code gets a 429 problem with {@code Retry-After}.
 */
final class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final RequestMatcher NAVIGATION =
            new OrRequestMatcher(pathPattern("/oauth2/authorization/**"), pathPattern("/login/oauth2/code/**"));

    private static final RequestMatcher XHR =
            new OrRequestMatcher(pathPattern("/webauthn/authenticate/options"), pathPattern("/login/webauthn"));

    private final LoginRateLimiter limiter;
    private final HandlerExceptionResolver resolver;
    private final String limitedPage;

    LoginRateLimitFilter(LoginRateLimiter limiter, HandlerExceptionResolver resolver, IdentityProperties properties) {
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
        OptionalLong retryAfter = limiter.tryAcquire(request.getRemoteAddr());
        if (retryAfter.isEmpty()) {
            chain.doFilter(request, response);
        } else if (NAVIGATION.matches(request)) {
            response.sendRedirect(limitedPage);
        } else {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter.getAsLong()));
            if (resolver.resolveException(request, response, null, new RateLimitedException()) == null) {
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value());
            }
        }
    }
}
