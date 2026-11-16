package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.liberdadefamilia.identity.internal.UserAccounts.UserRecord;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

class PasskeyUserDetailsServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final UserAccounts accounts = mock(UserAccounts.class);
    private final PasskeyUserDetailsService service = new PasskeyUserDetailsService(accounts);

    @Test
    void loadsActiveUser() {
        when(accounts.findActive(userId))
                .thenReturn(Optional.of(new UserRecord(userId, "Ana Teste", "ana@example.com", null)));

        UserDetails user = service.loadUserByUsername(userId.toString());

        assertThat(user.getUsername()).isEqualTo(userId.toString());
        assertThat(user.getPassword()).isNull();
        assertThat(user.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void rejectsUnknownInactiveOrMalformed() {
        when(accounts.findActive(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername(UUID.randomUUID().toString()))
                .isInstanceOf(UsernameNotFoundException.class);
        assertThatThrownBy(() -> service.loadUserByUsername(userId.toString()))
                .isInstanceOf(UsernameNotFoundException.class);
        assertThatThrownBy(() -> service.loadUserByUsername("not-a-user-id"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    /** The JDBC session keeps the signed-in authentication with Java serialization. */
    @Test
    void webAuthnAuthenticationIsSerializable() throws Exception {
        var entity = ImmutablePublicKeyCredentialUserEntity.builder()
                .id(Bytes.random())
                .name(userId.toString())
                .displayName("Ana Teste")
                .build();
        List<GrantedAuthority> authorities = AuthorityUtils.createAuthorityList("ROLE_USER");
        authorities.add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.WEBAUTHN_AUTHORITY));
        var authentication = new WebAuthnAuthentication(entity, authorities);

        Object copy = deserialize(serialize(authentication));

        assertThat(copy).isInstanceOf(WebAuthnAuthentication.class);
        assertThat(((WebAuthnAuthentication) copy).getName()).isEqualTo(userId.toString());
        assertThat(((WebAuthnAuthentication) copy).isAuthenticated()).isTrue();
    }

    private static byte[] serialize(Object object) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }
}
