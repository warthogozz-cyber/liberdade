package br.com.liberdadefamilia.identity;

import jakarta.servlet.http.HttpServletRequest;

/** What the sign-in leaves in the session to help the first steps, and how to drop it once it served its purpose. */
public interface SignInHints {

    /** Drops Google's family name from the session; called once the family exists and the hint has no more use. */
    void forgetFamilyName(HttpServletRequest request);
}
