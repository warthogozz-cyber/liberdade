package br.com.liberdadefamilia.identity.internal;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import com.webauthn4j.util.exception.WebAuthnException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import tools.jackson.core.JacksonException;

/**
 * Keeps {@code POST /webauthn/register} in the problem contract. Spring's registration filter answers a body it cannot
 * read, or a registration with no creation options in the session, with a bare 400, and lets a failed verification
 * escape as an exception. Both become the 400 {@code passkey.rejected} problem. Any other failure, such as the
 * database being down, is a server fault: it goes to the same resolver as every other failure, which answers it with
 * the generic 500.
 *
 * <p>Sits right before that filter. It never calls the rest of the chain on this path, so its bare 400 is read from
 * the status it leaves on the response, which is not committed yet; no request body is buffered.
 */
final class PasskeyRegistrationProblemFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PasskeyRegistrationProblemFilter.class);

    private static final RequestMatcher REGISTER = pathPattern(HttpMethod.POST, "/webauthn/register");

    private final HandlerExceptionResolver resolver;

    PasskeyRegistrationProblemFilter(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !REGISTER.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (WebAuthnException | JacksonException | IllegalArgumentException | NullPointerException rejected) {
            // From the credential the client sent: webauthn4j rejects a bad attestation, signature, challenge, origin
            // or rpId, and lets Jackson's error through for client data that is not JSON. Spring does not check the
            // credential for missing parts, so an incomplete one fails on a null, and a known one on an argument.
            reject(request, response, rejected.getClass().getSimpleName());
            return;
        } catch (RuntimeException failure) {
            resolve(request, response, failure, HttpStatus.INTERNAL_SERVER_ERROR);
            return;
        }
        if (!response.isCommitted() && response.getStatus() == HttpStatus.BAD_REQUEST.value()) {
            reject(request, response, "unreadable body or no creation options");
        }
    }

    /** The reason is a fixed phrase or a class name, never the credential or the exception message. */
    private void reject(HttpServletRequest request, HttpServletResponse response, String reason) throws IOException {
        log.debug("Passkey registration rejected: {}", reason);
        resolve(request, response, new PasskeyRejectedException(), HttpStatus.BAD_REQUEST);
    }

    private void resolve(
            HttpServletRequest request, HttpServletResponse response, Exception failure, HttpStatus unresolved)
            throws IOException {
        if (resolver.resolveException(request, response, null, failure) == null) {
            response.sendError(unresolved.value());
        }
    }
}
