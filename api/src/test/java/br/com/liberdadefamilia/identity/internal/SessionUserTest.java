package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionUserTest {

    private static final UUID USER_ID = UUID.fromString("0190a3f0-0000-7000-8000-000000000003");

    @Test
    void authenticatesWithTheUserIdAlone() {
        var authentication = new SessionUserAuthentication(new SessionUser(USER_ID));

        assertThat(authentication.getName()).isEqualTo(USER_ID.toString());
        assertThat(authentication.getPrincipal()).isEqualTo(new SessionUser(USER_ID));
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    @Test
    void survivesJavaSerialization() throws Exception {
        var authentication = new SessionUserAuthentication(new SessionUser(USER_ID));

        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(authentication);
        }
        Object copy;
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            copy = in.readObject();
        }

        assertThat(copy).isInstanceOfSatisfying(SessionUserAuthentication.class, restored -> {
            assertThat(restored.getName()).isEqualTo(USER_ID.toString());
            assertThat(restored.getPrincipal()).isEqualTo(new SessionUser(USER_ID));
            assertThat(restored.isAuthenticated()).isTrue();
        });
    }
}
