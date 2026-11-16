package br.com.liberdadefamilia.identity.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Finds the person a passkey signs in. Spring passes the {@code user_entities.name} of the passkey, which is the
 * {@code users.id} (D18); only active users are found.
 *
 * <p>Not a bean: a {@code UserDetailsService} bean would also turn on username and password sign-in.
 */
final class PasskeyUserDetailsService implements UserDetailsService {

    private final UserAccounts accounts;

    PasskeyUserDetailsService(UserAccounts accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userIdOf(username)
                .flatMap(accounts::findActive)
                .<UserDetails>map(user -> new PasskeyUser(user.id().toString()))
                .orElseThrow(() -> new UsernameNotFoundException("No active user for this passkey"));
    }

    /** The {@code users.id} in a passkey name. Anything else, even the same id spelled another way, is none. */
    static Optional<UUID> userIdOf(String name) {
        try {
            UUID id = UUID.fromString(name);
            return id.toString().equals(name) ? Optional.of(id) : Optional.empty();
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /** A user with no password: passkeys are checked by their signature, never against {@link #getPassword()}. */
    private record PasskeyUser(String userId) implements UserDetails {

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }

        @Override
        public @Nullable String getPassword() {
            return null;
        }

        @Override
        public String getUsername() {
            return userId;
        }
    }
}
