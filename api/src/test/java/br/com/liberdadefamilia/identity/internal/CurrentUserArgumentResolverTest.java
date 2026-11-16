package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.liberdadefamilia.identity.CurrentUser;
import br.com.liberdadefamilia.identity.internal.UserAccounts.UserRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;

class CurrentUserArgumentResolverTest {

    private static final UUID USER_ID = UUID.fromString("0190a3f0-0000-7000-8000-000000000001");

    private final UserAccounts accounts = mock(UserAccounts.class);
    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(accounts);
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unused")
    static class Probe {
        void handle(CurrentUser user, String other) {}
    }

    private static MethodParameter parameter(int index) throws NoSuchMethodException {
        return new MethodParameter(Probe.class.getDeclaredMethod("handle", CurrentUser.class, String.class), index);
    }

    private Object resolve() throws Exception {
        return resolver.resolveArgument(parameter(0), null, new ServletWebRequest(request), null);
    }

    private static void signIn(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static Authentication sessionUser(UUID userId) {
        return new SessionUserAuthentication(new SessionUser(userId));
    }

    private static Authentication passkey(String name) {
        return UsernamePasswordAuthenticationToken.authenticated(name, null, List.of());
    }

    @Test
    void supportsOnlyCurrentUserParameters() throws Exception {
        assertThat(resolver.supportsParameter(parameter(0))).isTrue();
        assertThat(resolver.supportsParameter(parameter(1))).isFalse();
    }

    @Test
    void withoutAuthenticationFails() {
        assertThatThrownBy(this::resolve).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void anonymousCountsAsUnauthenticated() {
        signIn(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThatThrownBy(this::resolve).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void unknownUserFails() {
        signIn(passkey(UUID.randomUUID().toString()));

        assertThatThrownBy(this::resolve).isInstanceOf(InsufficientAuthenticationException.class);
    }

    @Test
    void nameThatIsNotAnIdFails() {
        signIn(passkey("not-a-user-id"));

        assertThatThrownBy(this::resolve).isInstanceOf(InsufficientAuthenticationException.class);
    }

    @Test
    void userWhoIsGoneFails() {
        when(accounts.findActive(USER_ID)).thenReturn(Optional.empty());
        signIn(sessionUser(USER_ID));

        assertThatThrownBy(this::resolve).isInstanceOf(InsufficientAuthenticationException.class);
    }

    @Test
    void userWhoAskedForDeletionIsResolvedWithTheDate() throws Exception {
        Instant requested = Instant.parse("2026-11-01T12:00:00Z");
        when(accounts.findActive(USER_ID))
                .thenReturn(Optional.of(new UserRecord(USER_ID, "Ana Teste", null, requested)));
        signIn(sessionUser(USER_ID));

        assertThat(resolve()).isEqualTo(new CurrentUser(USER_ID, "Ana Teste", null, requested));
    }

    @Test
    void sessionWithHintGivesFamilyNameHint() throws Exception {
        when(accounts.findActive(USER_ID))
                .thenReturn(Optional.of(new UserRecord(USER_ID, "Ana Teste", "ana@example.com", null)));
        signIn(sessionUser(USER_ID));
        request.getSession().setAttribute(SessionSignInHints.FAMILY_NAME_HINT, "Teste");

        assertThat(resolve()).isEqualTo(new CurrentUser(USER_ID, "Ana Teste", "Teste", null));
    }

    @Test
    void sessionWithoutHintHasNoFamilyNameHint() throws Exception {
        when(accounts.findActive(USER_ID))
                .thenReturn(Optional.of(new UserRecord(USER_ID, "Ana Teste", "ana@example.com", null)));
        signIn(sessionUser(USER_ID));
        request.getSession();

        assertThat(resolve()).isEqualTo(new CurrentUser(USER_ID, "Ana Teste", null, null));
    }

    @Test
    void passkeyUserHasNoFamilyNameHint() throws Exception {
        when(accounts.findActive(USER_ID)).thenReturn(Optional.of(new UserRecord(USER_ID, "Ana Teste", null, null)));
        signIn(passkey(USER_ID.toString()));

        assertThat(resolve()).isEqualTo(new CurrentUser(USER_ID, "Ana Teste", null, null));
    }
}
