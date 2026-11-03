package br.com.liberdadefamilia.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * A passkey that fails to verify gets the same 401 problem as any other request, whatever went wrong with it, so the
 * answer never tells which accounts exist. A server fault behind the sign-in is not a failed passkey: it gets the
 * generic 500 problem, neither a 401 that hides the outage nor Boot's error page.
 *
 * <p>Spring's passkey provider wraps every runtime exception, a database failure included, in a
 * {@code BadCredentialsException}, so a server fault is told apart by its cause as well as by its type.
 */
final class PasskeySignInFailureHandler implements AuthenticationFailureHandler {

    /** Bounded, since a cause chain can loop. */
    private static final int MAX_CAUSES = 16;

    private final SecurityProblemHandlers problems;
    private final HandlerExceptionResolver resolver;

    PasskeySignInFailureHandler(SecurityProblemHandlers problems, HandlerExceptionResolver resolver) {
        this.problems = problems;
        this.resolver = resolver;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        if (!isServerFault(exception)) {
            problems.commence(request, response, exception);
            return;
        }
        // Not an AuthenticationException, which the resolver would answer with a 401.
        var fault = new IllegalStateException("Passkey sign-in could not complete", exception);
        if (resolver.resolveException(request, response, null, fault) == null) {
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private static boolean isServerFault(AuthenticationException exception) {
        if (exception instanceof AuthenticationServiceException) {
            return true;
        }
        Throwable cause = exception.getCause();
        for (int depth = 0; cause != null && depth < MAX_CAUSES; depth++, cause = cause.getCause()) {
            if (cause instanceof DataAccessException) {
                return true;
            }
        }
        return false;
    }
}
