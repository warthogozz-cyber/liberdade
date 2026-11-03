package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

@ExtendWith(OutputCaptureExtension.class)
class LiberdadeOidcUserServiceTest {

    @Test
    void databaseFailureIsASignInFailureThatLogsNoRowValues(CapturedOutput output) {
        UserAccounts accounts = mock(UserAccounts.class);
        when(accounts.signInWithGoogle(anyString(), anyString(), any()))
                .thenThrow(new DataIntegrityViolationException(
                        "Key (subject)=(g-secret) violates ana@example.com",
                        new IllegalStateException("cause with Ana Teste")));
        Instant now = Instant.now();
        var idToken = new OidcIdToken(
                "id-token",
                now,
                now.plusSeconds(60),
                Map.of("sub", "g-secret", "name", "Ana Teste", "email", "ana@example.com"));
        var fromGoogle = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        var service = new LiberdadeOidcUserService(request -> fromGoogle, accounts);
        var google = CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId("test-client")
                .clientSecret("test-secret")
                .build();
        var accessToken =
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", now, now.plusSeconds(60));

        var failure = catchThrowableOfType(
                OAuth2AuthenticationException.class,
                () -> service.loadUser(new OidcUserRequest(google, accessToken, idToken)));

        assertThat(failure.getError().getErrorCode()).isEqualTo("server_error");
        assertThat(failure).hasNoCause();
        assertThat(output)
                .contains(DataIntegrityViolationException.class.getName())
                .doesNotContain("g-secret", "ana@example.com", "Ana Teste");
    }
}
