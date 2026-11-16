package br.com.liberdadefamilia.identity.internal;

import java.io.Serial;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * The authentication stored in the session after either sign-in. It names the user by id and carries no token, e-mail
 * or name; anything else about the user is read from the database on each request.
 */
public final class SessionUserAuthentication extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final SessionUser user;

    public SessionUserAuthentication(SessionUser user) {
        super(List.of());
        this.user = user;
        setAuthenticated(true);
    }

    @Override
    public String getName() {
        return user.userId().toString();
    }

    @Override
    public SessionUser getPrincipal() {
        return user;
    }

    @Override
    public @Nullable Object getCredentials() {
        return null;
    }
}
