package br.com.liberdadefamilia.identity;

import jakarta.servlet.http.HttpServletRequest;

/** The check behind {@link RequiresRecentSignIn}, for routes where only part of the request is sensitive. */
public interface RecentSignInCheck {

    /** Returns when the sign-in is recent; otherwise throws the 403 {@code auth.reauthentication-required}. */
    void require(HttpServletRequest request);
}
