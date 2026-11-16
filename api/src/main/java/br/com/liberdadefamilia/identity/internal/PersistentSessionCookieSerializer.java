package br.com.liberdadefamilia.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * The session cookie: {@code __Host-sid}, on path /, Secure, HttpOnly and SameSite=Lax. It lasts the browser session
 * until someone signs in; a request marked {@link #PERSISTENT} writes it with a Max-Age of 30 days instead, so an
 * installed app reopens signed in. An expiring cookie (empty value) keeps its Max-Age of 0.
 *
 * <p>A bean of its own rather than {@code server.servlet.session.cookie.*}, which Boot ignores whenever it sees a
 * servlet context it did not start (a WAR, or MockMvc in tests).
 */
public class PersistentSessionCookieSerializer implements CookieSerializer {

    /** Request attribute: when present, the session cookie this request writes lasts {@link #MAX_AGE_SECONDS}. */
    public static final String PERSISTENT = "liberdade.persistentCookie";

    /** 30 days, the same as the signed-in session's idle timeout. */
    public static final int MAX_AGE_SECONDS = 2_592_000;

    private final DefaultCookieSerializer delegate = new DefaultCookieSerializer();

    public PersistentSessionCookieSerializer() {
        delegate.setCookieName(SecurityConfig.SESSION_COOKIE);
        delegate.setCookiePath("/");
        delegate.setUseSecureCookie(true);
        delegate.setUseHttpOnlyCookie(true);
        delegate.setSameSite("Lax");
    }

    @Override
    public void writeCookieValue(CookieValue cookieValue) {
        String value = cookieValue.getCookieValue();
        if (value != null && !value.isEmpty() && cookieValue.getRequest().getAttribute(PERSISTENT) != null) {
            // Per write, on the value: the delegate is shared across requests.
            cookieValue.setCookieMaxAge(MAX_AGE_SECONDS);
        }
        delegate.writeCookieValue(cookieValue);
    }

    @Override
    public List<String> readCookieValues(HttpServletRequest request) {
        return delegate.readCookieValues(request);
    }
}
