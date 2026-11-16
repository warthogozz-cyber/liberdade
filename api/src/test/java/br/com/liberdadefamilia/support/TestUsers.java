package br.com.liberdadefamilia.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import br.com.liberdadefamilia.identity.internal.SessionSignInHints;
import br.com.liberdadefamilia.identity.internal.SessionUser;
import br.com.liberdadefamilia.identity.internal.SessionUserAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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

    /** Signs the request in as the user and, when there is a family name, puts it in the session as Google's hint. */
    public static RequestPostProcessor signedIn(UUID userId, @Nullable String familyName) {
        RequestPostProcessor signIn = authentication(new SessionUserAuthentication(new SessionUser(userId)));
        return request -> {
            if (familyName != null) {
                request.setAttribute(FAMILY_NAME_HINT, familyName);
            }
            return signIn.postProcessRequest(request);
        };
    }

    /**
     * Puts the hint {@link #signedIn} carries into the session the request ends up with. A post-processor cannot do it
     * itself: it only sees the mock request's own session, and Spring Session hands the application a JDBC one.
     */
    public static class FamilyNameHintFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (request.getAttribute(FAMILY_NAME_HINT) instanceof String familyName) {
                request.getSession().setAttribute(SessionSignInHints.FAMILY_NAME_HINT, familyName);
            }
            chain.doFilter(request, response);
        }
    }
}
