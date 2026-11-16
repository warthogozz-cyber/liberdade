package br.com.liberdadefamilia.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Stores only the user id in the session (LGPD). Google's and the passkey's authentications are swapped for a
 * {@link SessionUserAuthentication} before the delegate saves them, so the id token, the e-mail and the name never
 * reach {@code spring_session_attributes}. Google's family name, when there is one, stays apart as a
 * {@link SessionSignInHints#FAMILY_NAME_HINT hint} until the family is created.
 */
public class MinimalSecurityContextRepository implements SecurityContextRepository {

    private final SecurityContextRepository delegate;

    public MinimalSecurityContextRepository(SecurityContextRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = context.getAuthentication();
        if (authentication == null || authentication instanceof SessionUserAuthentication) {
            delegate.saveContext(context, request, response);
            return;
        }
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            String familyName = oidcUser.getFamilyName();
            if (familyName != null && !familyName.isBlank()) {
                request.getSession().setAttribute(SessionSignInHints.FAMILY_NAME_HINT, familyName);
            }
        }
        var minimal = new SessionUserAuthentication(new SessionUser(UUID.fromString(authentication.getName())));
        delegate.saveContext(new SecurityContextImpl(minimal), request, response);
    }

    @Override
    public DeferredSecurityContext loadDeferredContext(HttpServletRequest request) {
        return delegate.loadDeferredContext(request);
    }

    /** Still abstract in Spring Security 7, though deprecated; nothing here calls it. */
    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
        return delegate.loadContext(requestResponseHolder);
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        return delegate.containsContext(request);
    }
}
