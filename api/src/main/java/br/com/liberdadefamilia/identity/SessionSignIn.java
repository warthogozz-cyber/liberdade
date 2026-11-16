package br.com.liberdadefamilia.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;

/** Signs a person in for the flows Spring Security does not run itself (invitation, recovery). */
public interface SessionSignIn {

    /** Gives the session a new id, stores the minimal sign-in and its instant, and publishes {@link SignedIn}. */
    void signIn(HttpServletRequest request, HttpServletResponse response, UUID userId, SignInMethod method);
}
