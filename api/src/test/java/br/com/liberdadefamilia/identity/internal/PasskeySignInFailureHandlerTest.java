package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * What a failed passkey sign-in hands to the resolver: the {@code AuthenticationException} itself, which it answers
 * with the 401 {@code auth.required} problem, or, for a server fault, an exception that is not one, which it answers
 * with the generic 500.
 */
class PasskeySignInFailureHandlerTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login/webauthn");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final List<Exception> resolved = new ArrayList<>();
    private final HandlerExceptionResolver resolver = (request, response, handler, exception) -> {
        resolved.add(exception);
        return new ModelAndView();
    };
    private final PasskeySignInFailureHandler handler =
            new PasskeySignInFailureHandler(new SecurityProblemHandlers(resolver), resolver);

    @Test
    void failedVerificationIsAnAuthenticationFailure() throws Exception {
        var failure = new BadCredentialsException("bad signature", new IllegalArgumentException("signature"));

        handler.onAuthenticationFailure(request, response, failure);

        assertThat(resolved).containsExactly(failure);
    }

    @Test
    void databaseFailureWrappedByTheProviderIsAServerFault() throws Exception {
        var outage = new DataAccessResourceFailureException("connection refused");

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("refused", outage));

        assertServerFault(outage);
    }

    @Test
    void authenticationServiceFailureIsAServerFault() throws Exception {
        handler.onAuthenticationFailure(request, response, new InternalAuthenticationServiceException("down"));
        handler.onAuthenticationFailure(request, response, new AuthenticationServiceException("down"));

        assertThat(resolved).hasSize(2).noneMatch(AuthenticationException.class::isInstance);
    }

    @Test
    void unresolvedServerFaultFallsBackTo500() throws Exception {
        var unresolved = new PasskeySignInFailureHandler(
                new SecurityProblemHandlers((request, response, handler, exception) -> null),
                (request, response, handler, exception) -> null);

        unresolved.onAuthenticationFailure(request, response, new AuthenticationServiceException("down"));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.isCommitted()).isTrue();
    }

    private void assertServerFault(Throwable cause) {
        assertThat(resolved).singleElement().isNotInstanceOf(AuthenticationException.class);
        assertThat(resolved.getFirst()).hasRootCause(cause);
    }
}
