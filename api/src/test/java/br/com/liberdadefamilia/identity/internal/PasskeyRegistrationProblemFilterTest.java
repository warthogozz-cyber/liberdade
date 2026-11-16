package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * The paths of the registration problem filter that a full ceremony cannot reach cheaply; {@code PasskeyIT} covers the
 * rejections end to end.
 */
@ExtendWith(OutputCaptureExtension.class)
class PasskeyRegistrationProblemFilterTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webauthn/register");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final List<Exception> resolved = new ArrayList<>();
    private final HandlerExceptionResolver resolver = (request, response, handler, exception) -> {
        resolved.add(exception);
        return new ModelAndView();
    };

    @Test
    void serverFaultIsNotARejection() throws Exception {
        var outage = new DataAccessResourceFailureException("connection refused");

        new PasskeyRegistrationProblemFilter(resolver).doFilter(request, response, failingWith(outage));

        assertThat(resolved).containsExactly(outage);
    }

    @Test
    void nullOrArgumentFailureIsARejectionLoggedAtWarnWithTheTypeOnly(CapturedOutput output) throws Exception {
        var filter = new PasskeyRegistrationProblemFilter(resolver);

        filter.doFilter(request, response, failingWith(new NullPointerException("credential-id-secret")));
        filter.doFilter(
                new MockHttpServletRequest("POST", "/webauthn/register"),
                new MockHttpServletResponse(),
                failingWith(new IllegalArgumentException("user-handle-secret")));

        assertThat(resolved).hasSize(2).allMatch(PasskeyRejectedException.class::isInstance);
        assertThat(output)
                .containsPattern("WARN .*PasskeyRegistrationProblemFilter.*NullPointerException")
                .containsPattern("WARN .*PasskeyRegistrationProblemFilter.*IllegalArgumentException")
                .doesNotContain("credential-id-secret", "user-handle-secret");
    }

    @Test
    void successIsLeftAlone() throws Exception {
        new PasskeyRegistrationProblemFilter(resolver).doFilter(request, response, new MockFilterChain());

        assertThat(resolved).isEmpty();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void unresolvedRejectionFallsBackTo400() throws Exception {
        var filter = new PasskeyRegistrationProblemFilter((request, response, handler, exception) -> null);

        filter.doFilter(request, response, failingWith(new IllegalArgumentException("already exists")));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.isCommitted()).isTrue();
    }

    private static FilterChain failingWith(RuntimeException failure) {
        return (request, response) -> {
            throw failure;
        };
    }
}
