package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.identity.DeviceLabel;
import br.com.liberdadefamilia.identity.SignInMethod;
import br.com.liberdadefamilia.identity.SignedIn;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
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
 * {@link SessionSignInHints#FAMILY_NAME_HINT hint} until the family is created. A new sign-in also records its instant
 * ({@link RecentSignInInterceptor#AUTHENTICATED_AT}) and publishes {@link SignedIn}.
 */
public class MinimalSecurityContextRepository implements SecurityContextRepository {

    private final SecurityContextRepository delegate;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public MinimalSecurityContextRepository(
            SecurityContextRepository delegate, Clock clock, ApplicationEventPublisher events) {
        this.delegate = delegate;
        this.clock = clock;
        this.events = events;
    }

    @Override
    public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = context.getAuthentication();
        if (authentication == null || authentication instanceof SessionUserAuthentication) {
            delegate.saveContext(context, request, response);
            return;
        }
        HttpSession session = request.getSession();
        SignInMethod method = SignInMethod.PASSKEY;
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            method = SignInMethod.GOOGLE;
            String familyName = oidcUser.getFamilyName();
            if (familyName != null && !familyName.isBlank()) {
                session.setAttribute(SessionSignInHints.FAMILY_NAME_HINT, familyName);
            }
        }
        UUID userId = UUID.fromString(authentication.getName());
        session.setAttribute(RecentSignInInterceptor.AUTHENTICATED_AT, clock.instant());
        var minimal = new SessionUserAuthentication(new SessionUser(userId));
        delegate.saveContext(new SecurityContextImpl(minimal), request, response);
        events.publishEvent(new SignedIn(userId, method, DeviceLabel.parse(request.getHeader(HttpHeaders.USER_AGENT))));
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
