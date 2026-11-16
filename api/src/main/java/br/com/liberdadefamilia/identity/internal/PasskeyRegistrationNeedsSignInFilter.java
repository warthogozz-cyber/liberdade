package br.com.liberdadefamilia.identity.internal;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Passkey registration options need a signed-in user. Spring answers anyone else with a bare 400; this gives the same
 * 401 problem as every other endpoint that needs sign-in.
 */
final class PasskeyRegistrationNeedsSignInFilter extends OncePerRequestFilter {

    private static final RequestMatcher REGISTER_OPTIONS = pathPattern(HttpMethod.POST, "/webauthn/register/options");

    private final SecurityProblemHandlers problems;
    private final SecurityContextHolderStrategy contexts;
    private final AuthenticationTrustResolver trust = new AuthenticationTrustResolverImpl();

    PasskeyRegistrationNeedsSignInFilter(SecurityProblemHandlers problems, SecurityContextHolderStrategy contexts) {
        this.problems = problems;
        this.contexts = contexts;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !REGISTER_OPTIONS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!trust.isAuthenticated(contexts.getContext().getAuthentication())) {
            problems.commence(
                    request, response, new InsufficientAuthenticationException("Sign in to register a passkey"));
            return;
        }
        chain.doFilter(request, response);
    }
}
