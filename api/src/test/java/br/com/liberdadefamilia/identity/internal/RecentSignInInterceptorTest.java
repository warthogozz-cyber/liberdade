package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RecentSignInInterceptorTest {

    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

    private final RecentSignInInterceptor interceptor = new RecentSignInInterceptor(Clock.fixed(NOW, ZoneOffset.UTC));
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void exactlyTenMinutesPasses() {
        request.getSession().setAttribute(RecentSignInInterceptor.AUTHENTICATED_AT, NOW.minus(Duration.ofMinutes(10)));

        assertThatCode(() -> interceptor.require(request)).doesNotThrowAnyException();
    }

    @Test
    void tenMinutesAndOneNanosecondIsTurnedDown() {
        request.getSession()
                .setAttribute(
                        RecentSignInInterceptor.AUTHENTICATED_AT,
                        NOW.minus(Duration.ofMinutes(10)).minusNanos(1));

        assertThatThrownBy(() -> interceptor.require(request))
                .isInstanceOfSatisfying(
                        ReauthenticationRequiredException.class,
                        e -> assertThat(e.code()).isEqualTo("auth.reauthentication-required"));
    }

    @Test
    void missingInstantOrSessionIsTurnedDown() {
        assertThatThrownBy(() -> interceptor.require(request)).isInstanceOf(ReauthenticationRequiredException.class);

        request.getSession();
        assertThatThrownBy(() -> interceptor.require(request)).isInstanceOf(ReauthenticationRequiredException.class);
    }
}
