package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.servlet.ModelAndView;

class SecurityProblemHandlersTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void unresolvedAuthenticationFailureFallsBackTo401() throws Exception {
        var handlers = new SecurityProblemHandlers((request, response, handler, exception) -> null);

        handlers.commence(request, response, new InsufficientAuthenticationException("sign in"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.isCommitted()).isTrue();
    }

    @Test
    void unresolvedAccessDeniedFallsBackTo403() throws Exception {
        var handlers = new SecurityProblemHandlers((request, response, handler, exception) -> null);

        handlers.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.isCommitted()).isTrue();
    }

    @Test
    void unresolvedFailureOnACommittedResponseSendsNothing() throws Exception {
        var handlers = new SecurityProblemHandlers((request, response, handler, exception) -> null);
        response.setStatus(200);
        response.setCommitted(true);

        // sendError would throw IllegalStateException on a committed response.
        handlers.commence(request, response, new InsufficientAuthenticationException("sign in"));
        handlers.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void resolvedFailuresAreLeftToTheResolver() throws Exception {
        var handlers = new SecurityProblemHandlers((request, response, handler, exception) -> {
            response.setStatus(418);
            return new ModelAndView();
        });

        handlers.commence(request, response, new InsufficientAuthenticationException("sign in"));
        handlers.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(418);
        assertThat(response.isCommitted()).isFalse();
    }
}
