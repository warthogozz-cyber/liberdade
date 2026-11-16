package br.com.liberdadefamilia.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import br.com.liberdadefamilia.identity.internal.LiberdadeOidcUser;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Creates users straight in the database and signs requests in as them, the way a Google login would. */
public class TestUsers {

    private final JdbcClient jdbc;

    public TestUsers(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID create(String displayName, @Nullable String email) {
        return jdbc.sql("insert into users (display_name, email) values (:displayName, :email) returning id")
                .param("displayName", displayName)
                .param("email", email)
                .query(UUID.class)
                .single();
    }

    public static RequestPostProcessor signedIn(UUID userId, @Nullable String familyName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(IdTokenClaimNames.SUB, "google-" + userId);
        if (familyName != null) {
            claims.put(StandardClaimNames.FAMILY_NAME, familyName);
        }
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("test-id-token", now, now.plusSeconds(3600), claims);
        return oidcLogin()
                .oidcUser(
                        new LiberdadeOidcUser(userId, List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken, null));
    }
}
