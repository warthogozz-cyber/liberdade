package br.com.liberdadefamilia.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import br.com.liberdadefamilia.identity.internal.RecentSignInInterceptor;
import br.com.liberdadefamilia.identity.internal.SessionSignInHints;
import br.com.liberdadefamilia.identity.internal.SessionUser;
import br.com.liberdadefamilia.identity.internal.SessionUserAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Creates users straight in the database and signs requests in as them, with the minimal authentication a real sign-in
 * leaves in the session.
 */
public class TestUsers {

    /** Carries the family name hint from {@link #signedIn} to {@link FamilyNameHintFilter}. */
    private static final String FAMILY_NAME_HINT = TestUsers.class.getName() + ".familyNameHint";

    /** Carries how many minutes ago the sign-in happened, from {@link #signedIn} to {@link FamilyNameHintFilter}. */
    private static final String SIGNED_IN_MINUTES_AGO = TestUsers.class.getName() + ".signedInMinutesAgo";

    private final JdbcClient jdbc;

    public TestUsers(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID create(String displayName, @Nullable String email) {
        return jdbc.sql("insert into users (display_name, email) values (:displayName, :email) returning id")
                .param("displayName", displayName)
                .param("email", email)
                .query(UUID.class)
                .single();
    }

    /**
     * Signs the request in as the user, just now, and, when there is a family name, puts it in the session as Google's
     * hint.
     */
    public static RequestPostProcessor signedIn(UUID userId, @Nullable String familyName) {
        return signedIn(userId, familyName, 0);
    }

    /** Signs the request in as the user, as of {@code minutes} ago: the old sign-in a sensitive action turns down. */
    public static RequestPostProcessor signedInMinutesAgo(UUID userId, int minutes) {
        return signedIn(userId, null, minutes);
    }

    private static RequestPostProcessor signedIn(UUID userId, @Nullable String familyName, int minutesAgo) {
        RequestPostProcessor signIn = authentication(new SessionUserAuthentication(new SessionUser(userId)));
        return request -> {
            request.setAttribute(SIGNED_IN_MINUTES_AGO, minutesAgo);
            if (familyName != null) {
                request.setAttribute(FAMILY_NAME_HINT, familyName);
            }
            return signIn.postProcessRequest(request);
        };
    }

    /**
     * Puts what {@link #signedIn} carries (the hint and the sign-in instant, from the application's own clock) into the
     * session the request ends up with. A post-processor cannot do it itself: it only sees the mock request's own
     * session, and Spring Session hands the application a JDBC one.
     */
    public static class FamilyNameHintFilter extends OncePerRequestFilter {

        private final Clock clock;

        public FamilyNameHintFilter(Clock clock) {
            this.clock = clock;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (request.getAttribute(SIGNED_IN_MINUTES_AGO) instanceof Integer minutesAgo) {
                request.getSession()
                        .setAttribute(
                                RecentSignInInterceptor.AUTHENTICATED_AT,
                                clock.instant().minus(Duration.ofMinutes(minutesAgo)));
            }
            if (request.getAttribute(FAMILY_NAME_HINT) instanceof String familyName) {
                request.getSession().setAttribute(SessionSignInHints.FAMILY_NAME_HINT, familyName);
            }
            chain.doFilter(request, response);
        }
    }
}
