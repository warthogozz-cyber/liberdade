package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.identity.SignInHints;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/** Keeps the sign-in hints as session attributes, next to the minimal authentication. */
@Component
public class SessionSignInHints implements SignInHints {

    /** Google's {@code family_name}, kept only until the family is created, to suggest its name. */
    public static final String FAMILY_NAME_HINT = "liberdade.familyNameHint";

    @Override
    public void forgetFamilyName(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(FAMILY_NAME_HINT);
        }
    }
}
