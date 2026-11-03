package br.com.liberdadefamilia.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.identity.internal.LiberdadeOidcUserService;
import br.com.liberdadefamilia.identity.internal.UserAccounts;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LiberdadeOidcUserServiceIT {

    @Autowired
    UserAccounts accounts;

    @Autowired
    ClientRegistrationRepository clients;

    @Autowired
    JdbcClient jdbc;

    /** Runs the service with a fake delegate that returns what Google would have sent with these claims. */
    private OidcUser signIn(Map<String, Object> claims) {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("id-token", now, now.plusSeconds(60), claims);
        OidcUser fromGoogle = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        var service = new LiberdadeOidcUserService(request -> fromGoogle, accounts);
        var accessToken =
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", now, now.plusSeconds(60));
        return service.loadUser(new OidcUserRequest(clients.findByRegistrationId("google"), accessToken, idToken));
    }

    private UUID userIdOf(String subject) {
        return jdbc.sql("select user_id from auth_identities where provider = 'google' and subject = :subject")
                .param("subject", subject)
                .query(UUID.class)
                .single();
    }

    private Map<String, Object> userRow(UUID id) {
        return jdbc.sql("select display_name, email from users where id = :id")
                .param("id", id)
                .query()
                .singleRow();
    }

    @Test
    void firstSignInCreatesUserAndIdentity() {
        OidcUser user =
                signIn(Map.of("sub", "g-1", "name", "Ana Teste", "email", "ana@example.com", "family_name", "Teste"));

        UUID userId = userIdOf("g-1");
        assertThat(userRow(userId))
                .containsEntry("display_name", "Ana Teste")
                .containsEntry("email", "ana@example.com");
        assertThat(jdbc.sql("select count(*) from auth_identities where user_id = :id")
                        .param("id", userId)
                        .query(Long.class)
                        .single())
                .isOne();
        assertThat(user.getName()).isEqualTo(userId.toString());
        assertThat(user.getFamilyName()).isEqualTo("Teste");
    }

    @Test
    void secondSignInReusesUser() {
        OidcUser first = signIn(Map.of("sub", "g-2", "name", "Ana Teste", "email", "ana@example.com"));
        jdbc.sql("update auth_identities set last_used_at = '2000-01-01T00:00:00Z' where subject = 'g-2'")
                .update();

        OidcUser second = signIn(Map.of("sub", "g-2", "name", "Ana Renomeada", "email", "ana.nova@example.com"));

        UUID userId = userIdOf("g-2");
        assertThat(second.getName()).isEqualTo(first.getName()).isEqualTo(userId.toString());
        assertThat(userRow(userId))
                .containsEntry("display_name", "Ana Teste")
                .containsEntry("email", "ana.nova@example.com");
        OffsetDateTime lastUsedAt = jdbc.sql("select last_used_at from auth_identities where subject = 'g-2'")
                .query(OffsetDateTime.class)
                .single();
        assertThat(lastUsedAt).isAfter(OffsetDateTime.parse("2000-01-01T00:00:00Z"));
    }

    @Test
    void displayNameFallsBack() {
        signIn(Map.of("sub", "g-3", "given_name", "Bia", "email", "bia@example.com"));
        signIn(Map.of("sub", "g-4", "name", " ", "email", "bia@example.com"));
        signIn(Map.of("sub", "g-5"));

        assertThat(userRow(userIdOf("g-3"))).containsEntry("display_name", "Bia");
        assertThat(userRow(userIdOf("g-4"))).containsEntry("display_name", "bia");
        assertThat(userRow(userIdOf("g-5")))
                .containsEntry("display_name", "Você")
                .containsEntry("email", null);
    }

    @Test
    void longDisplayNameIsCutTo200Characters() {
        signIn(Map.of("sub", "g-6", "name", "A".repeat(250)));

        assertThat(userRow(userIdOf("g-6"))).containsEntry("display_name", "A".repeat(200));
    }
}
