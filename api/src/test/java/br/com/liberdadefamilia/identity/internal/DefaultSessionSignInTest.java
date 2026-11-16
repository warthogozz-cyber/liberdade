package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.liberdadefamilia.identity.DeviceLabel;
import br.com.liberdadefamilia.identity.SignInMethod;
import br.com.liberdadefamilia.identity.SignedIn;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

class DefaultSessionSignInTest {

    private static final UUID USER_ID = UUID.fromString("0190a3f0-0000-7000-8000-000000000006");

    private final SecurityContextRepository contexts = mock(SecurityContextRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC);
    private final List<Object> events = new ArrayList<>();
    private final DefaultSessionSignIn signIn = new DefaultSessionSignIn(contexts, clock, events::add);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearHolder() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void changesTheSessionIdAndRecordsTheSignIn() {
        request.addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0");
        String before = request.getSession().getId();

        signIn.signIn(request, response, USER_ID, SignInMethod.INVITATION);

        assertThat(request.getSession().getId()).isNotEqualTo(before);
        var captor = ArgumentCaptor.forClass(SecurityContext.class);
        verify(contexts).saveContext(captor.capture(), same(request), same(response));
        assertThat(captor.getValue().getAuthentication())
                .isEqualTo(new SessionUserAuthentication(new SessionUser(USER_ID)));
        assertThat(SecurityContextHolder.getContext()).isSameAs(captor.getValue());
        assertThat(request.getSession().getAttribute(RecentSignInInterceptor.AUTHENTICATED_AT))
                .isEqualTo(clock.instant());
        assertThat(request.getSession().getMaxInactiveInterval())
                .isEqualTo((int) SecurityConfig.SIGNED_IN_IDLE_TIMEOUT.toSeconds());
        assertThat(request.getAttribute(PersistentSessionCookieSerializer.PERSISTENT))
                .isEqualTo(Boolean.TRUE);
        assertThat(events)
                .containsExactly(new SignedIn(USER_ID, SignInMethod.INVITATION, new DeviceLabel("Edge", "Windows")));
    }

    @Test
    void createsTheSessionWhenThereIsNone() {
        signIn.signIn(request, response, USER_ID, SignInMethod.RECOVERY);

        assertThat(request.getSession(false)).isNotNull();
        verify(contexts).saveContext(any(), same(request), same(response));
        assertThat(events)
                .containsExactly(new SignedIn(USER_ID, SignInMethod.RECOVERY, new DeviceLabel("other", "other")));
    }
}
