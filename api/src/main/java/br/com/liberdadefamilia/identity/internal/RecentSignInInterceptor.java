package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.identity.RecentSignInCheck;
import br.com.liberdadefamilia.identity.RequiresRecentSignIn;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Turns down a {@link RequiresRecentSignIn} route when the session's sign-in instant is missing or older than
 * {@link #RECENT}. Routes without the annotation pass whatever the age.
 */
@Component
public class RecentSignInInterceptor implements HandlerInterceptor, RecentSignInCheck {

    /** Session attribute with the {@link Instant} of the sign-in. */
    public static final String AUTHENTICATED_AT = "liberdade.authenticatedAt";

    public static final Duration RECENT = Duration.ofMinutes(10);

    private final Clock clock;

    RecentSignInInterceptor(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method && method.hasMethodAnnotation(RequiresRecentSignIn.class)) {
            require(request);
        }
        return true;
    }

    @Override
    public void require(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null
                || !(session.getAttribute(AUTHENTICATED_AT) instanceof Instant at)
                || Duration.between(at, clock.instant()).compareTo(RECENT) > 0) {
            throw new ReauthenticationRequiredException();
        }
    }
}
