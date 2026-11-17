package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.identity.DeviceLabel;
import br.com.liberdadefamilia.identity.SessionSignIn;
import br.com.liberdadefamilia.identity.SignInMethod;
import br.com.liberdadefamilia.identity.SignedIn;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.util.Collections;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** The sign-in Spring Security does not run: same session id swap, minimal context and idle timeout as Google's. */
@Component
final class DefaultSessionSignIn implements SessionSignIn {

    private final SecurityContextRepository contexts;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    DefaultSessionSignIn(
            @Qualifier("securityContextRepository") SecurityContextRepository contexts,
            Clock clock,
            ApplicationEventPublisher events) {
        this.contexts = contexts;
        this.clock = clock;
        this.events = events;
    }

    @Override
    public void signIn(HttpServletRequest request, HttpServletResponse response, UUID userId, SignInMethod method) {
        if (request.getSession(false) == null) {
            request.getSession();
        } else {
            request.changeSessionId();
        }
        // Nothing the anonymous session held survives the sign-in. That includes the CSRF token, which the
        // session-backed repository keeps as an attribute: the next request asks for a new one.
        HttpSession session = request.getSession();
        for (String name : Collections.list(session.getAttributeNames())) {
            session.removeAttribute(name);
        }
        var authentication = new SessionUserAuthentication(new SessionUser(userId));
        SecurityContext context = new SecurityContextImpl(authentication);
        SecurityContextHolder.getContextHolderStrategy().setContext(context);
        contexts.saveContext(context, request, response);
        session.setAttribute(RecentSignInInterceptor.AUTHENTICATED_AT, clock.instant());
        SecurityConfig.signedInIdleTimeout().onAuthentication(authentication, request, response);
        events.publishEvent(new SignedIn(userId, method, DeviceLabel.parse(request.getHeader(HttpHeaders.USER_AGENT))));
    }
}
