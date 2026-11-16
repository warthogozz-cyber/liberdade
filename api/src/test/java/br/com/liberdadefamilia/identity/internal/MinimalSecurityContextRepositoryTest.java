package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

class MinimalSecurityContextRepositoryTest {

    private static final UUID USER_ID = UUID.fromString("0190a3f0-0000-7000-8000-000000000005");
    private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("OIDC_USER"));

    private final SecurityContextRepository delegate = mock(SecurityContextRepository.class);
    private final MinimalSecurityContextRepository repository = new MinimalSecurityContextRepository(delegate);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private SecurityContext saved() {
        var captor = ArgumentCaptor.forClass(SecurityContext.class);
        verify(delegate).saveContext(captor.capture(), same(request), same(response));
        return captor.getValue();
    }

    private static Authentication google(Map<String, Object> claims) {
        Instant now = Instant.now();
        var idToken = new OidcIdToken("id-token", now, now.plusSeconds(60), claims);
        return new OAuth2AuthenticationToken(
                new LiberdadeOidcUser(USER_ID, AUTHORITIES, idToken, null), AUTHORITIES, "google");
    }

    private static Authentication passkey() {
        var user = ImmutablePublicKeyCredentialUserEntity.builder()
                .id(Bytes.random())
                .name(USER_ID.toString())
                .displayName("Ana Teste")
                .build();
        return new WebAuthnAuthentication(user, List.of(new SimpleGrantedAuthority("FACTOR_WEBAUTHN")));
    }

    @Test
    void googleSignInKeepsTheUserIdAndTheFamilyNameHint() {
        var context = new SecurityContextImpl(
                google(Map.of("sub", "g-1", "family_name", "Teste", "email", "ana@example.com", "name", "Ana Teste")));

        repository.saveContext(context, request, response);

        assertThat(saved().getAuthentication()).isEqualTo(new SessionUserAuthentication(new SessionUser(USER_ID)));
        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession(false).getAttribute(SessionSignInHints.FAMILY_NAME_HINT))
                .isEqualTo("Teste");
    }

    @Test
    void googleSignInWithoutFamilyNameLeavesNoHint() {
        repository.saveContext(
                new SecurityContextImpl(google(Map.of("sub", "g-1", "family_name", " "))), request, response);

        assertThat(saved().getAuthentication()).isInstanceOf(SessionUserAuthentication.class);
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void passkeySignInKeepsTheUserIdWithoutHint() {
        repository.saveContext(new SecurityContextImpl(passkey()), request, response);

        assertThat(saved().getAuthentication()).isEqualTo(new SessionUserAuthentication(new SessionUser(USER_ID)));
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void minimalContextPassesAsItCame() {
        var context = new SecurityContextImpl(new SessionUserAuthentication(new SessionUser(USER_ID)));

        repository.saveContext(context, request, response);

        assertThat(saved()).isSameAs(context);
    }

    @Test
    void emptyContextPassesAsItCame() {
        var context = new SecurityContextImpl();

        repository.saveContext(context, request, response);

        assertThat(saved()).isSameAs(context);
    }

    @Test
    @SuppressWarnings("deprecation")
    void loadingAndCheckingOnlyDelegate() {
        var holder = new HttpRequestResponseHolder(request, response);
        var stored = new SecurityContextImpl(new SessionUserAuthentication(new SessionUser(USER_ID)));
        when(delegate.containsContext(any())).thenReturn(true);
        when(delegate.loadContext(holder)).thenReturn(stored);

        assertThat(repository.containsContext(request)).isTrue();
        assertThat(repository.loadContext(holder)).isSameAs(stored);
        repository.loadDeferredContext(request);

        verify(delegate).loadDeferredContext(same(request));
    }
}
