package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

class LiberdadeOidcUserTest {

    @Test
    void survivesJavaSerialization() throws Exception {
        UUID userId = UUID.fromString("0190a3f0-0000-7000-8000-000000000002");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var idToken =
                new OidcIdToken("id-token", now, now.plusSeconds(60), Map.of("sub", "g-1", "family_name", "Teste"));
        var user = new LiberdadeOidcUser(userId, List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken, null);

        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(user);
        }
        Object copy;
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            copy = in.readObject();
        }

        assertThat(copy).isInstanceOfSatisfying(LiberdadeOidcUser.class, restored -> {
            assertThat(restored.userId()).isEqualTo(userId);
            assertThat(restored.getName()).isEqualTo(userId.toString());
            assertThat(restored.getFamilyName()).isEqualTo("Teste");
            assertThat(restored.getSubject()).isEqualTo("g-1");
        });
    }
}
