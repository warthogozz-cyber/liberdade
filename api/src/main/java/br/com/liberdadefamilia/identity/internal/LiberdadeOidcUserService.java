package br.com.liberdadefamilia.identity.internal;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** Turns a Google sign-in into a Liberdade user, creating the user on the first sign-in. */
public class LiberdadeOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final Logger log = LoggerFactory.getLogger(LiberdadeOidcUserService.class);

    private static final String FALLBACK_NAME = "Você";
    private static final int MAX_NAME_LENGTH = 200;

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final UserAccounts accounts;

    public LiberdadeOidcUserService(OAuth2UserService<OidcUserRequest, OidcUser> delegate, UserAccounts accounts) {
        this.delegate = delegate;
        this.accounts = accounts;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser user = delegate.loadUser(request);
        UUID userId;
        try {
            userId = accounts.signInWithGoogle(user.getSubject(), displayNameFrom(user), user.getEmail());
        } catch (DataAccessException e) {
            // The message (and its cause) can quote row values such as the subject, name or email: log the type only.
            // Thrown as a sign-in failure, so the browser goes back to the sign-in page instead of the API's /error.
            log.error("Sign-in failed while saving the user: {}", e.getClass().getName());
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR));
        }
        return new LiberdadeOidcUser(userId, user.getAuthorities(), user.getIdToken(), user.getUserInfo());
    }

    /** The first non-blank of name, given name and the email's local part; otherwise "Você". At most 200 characters. */
    static String displayNameFrom(OidcUser user) {
        return Stream.of(user.getFullName(), user.getGivenName(), localPart(user.getEmail()))
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .findFirst()
                .map(LiberdadeOidcUserService::cut)
                .orElse(FALLBACK_NAME);
    }

    private static @Nullable String localPart(@Nullable String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at < 0 ? email : email.substring(0, at);
    }

    private static String cut(String name) {
        if (name.codePointCount(0, name.length()) <= MAX_NAME_LENGTH) {
            return name;
        }
        return name.substring(0, name.offsetByCodePoints(0, MAX_NAME_LENGTH)).strip();
    }
}
