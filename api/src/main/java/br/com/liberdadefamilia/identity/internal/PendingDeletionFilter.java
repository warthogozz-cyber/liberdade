package br.com.liberdadefamilia.identity.internal;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import br.com.liberdadefamilia.identity.internal.UserAccounts.UserRecord;
import br.com.liberdadefamilia.shared.DomainException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * During the 30-day grace of a deletion request (D23) the person can still sign in, see who they are, cancel the
 * request and export their data, and nothing else: any other {@code /v1} route, and passkey registration, answers 403
 * {@code account.deletion-pending}. Runs on authenticated requests only, so anonymous requests pass untouched. The sign-in and
 * logout routes live outside both prefixes, so they are never in its way.
 */
final class PendingDeletionFilter extends OncePerRequestFilter {

    /**
     * What a person with a pending deletion may still call. Repeating the request is one of them: it answers the same
     * date.
     */
    static final List<RequestMatcher> ALLOWED = List.of(
            pathPattern(HttpMethod.GET, "/v1/me"),
            pathPattern(HttpMethod.POST, "/v1/me/deletion"),
            pathPattern(HttpMethod.DELETE, "/v1/me/deletion"),
            pathPattern(HttpMethod.POST, "/v1/me/export"),
            pathPattern(HttpMethod.GET, "/v1/csrf"));

    /** Every API route, and passkey registration. */
    private static final RequestMatcher SCOPE =
            new OrRequestMatcher(pathPattern("/v1/**"), pathPattern("/webauthn/register/**"));

    private final AuthenticationTrustResolver trust = new AuthenticationTrustResolverImpl();
    private final UserAccounts accounts;
    private final HandlerExceptionResolver resolver;

    PendingDeletionFilter(UserAccounts accounts, HandlerExceptionResolver resolver) {
        this.accounts = accounts;
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // On the decoded, normalized path (the same one MVC routes on), never on the raw URI.
        return !SCOPE.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean pending = signedInUser().map(UserRecord::deletionRequestedAt).isPresent();
        if (pending && ALLOWED.stream().noneMatch(matcher -> matcher.matches(request))) {
            if (resolver.resolveException(request, response, null, new DeletionPendingException()) == null) {
                response.sendError(HttpStatus.FORBIDDEN.value());
            }
            return;
        }
        chain.doFilter(request, response);
    }

    private Optional<UserRecord> signedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !trust.isAuthenticated(authentication)) {
            return Optional.empty();
        }
        try {
            return accounts.findActive(UUID.fromString(authentication.getName()));
        } catch (IllegalArgumentException notAUserId) {
            // The argument resolver turns this one away.
            return Optional.empty();
        }
    }

    /** The account is in its grace period. */
    static final class DeletionPendingException extends DomainException {

        DeletionPendingException() {
            super(HttpStatus.FORBIDDEN.value(), "account.deletion-pending", "Sua conta está marcada para exclusão.");
        }
    }
}
