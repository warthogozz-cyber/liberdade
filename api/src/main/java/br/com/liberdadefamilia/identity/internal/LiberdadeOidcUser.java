package br.com.liberdadefamilia.identity.internal;

import java.io.Serial;
import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/**
 * A Google user tied to a Liberdade user. Its name is the Liberdade user id, which becomes the session's principal
 * name. It lives in the JDBC session, so it must stay serializable.
 */
public class LiberdadeOidcUser extends DefaultOidcUser {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;

    public LiberdadeOidcUser(
            UUID userId,
            Collection<? extends GrantedAuthority> authorities,
            OidcIdToken idToken,
            @Nullable OidcUserInfo userInfo) {
        super(authorities, idToken, userInfo, IdTokenClaimNames.SUB);
        this.userId = userId;
    }

    public UUID userId() {
        return userId;
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}
