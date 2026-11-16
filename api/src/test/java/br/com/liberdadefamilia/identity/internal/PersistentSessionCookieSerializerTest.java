package br.com.liberdadefamilia.identity.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer.CookieValue;

class PersistentSessionCookieSerializerTest {

    private final PersistentSessionCookieSerializer serializer = new PersistentSessionCookieSerializer();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void signedInSessionGetsMaxAge() {
        request.setAttribute(PersistentSessionCookieSerializer.PERSISTENT, Boolean.TRUE);

        serializer.writeCookieValue(new CookieValue(request, response, "session-id"));

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("__Host-sid=")
                .contains("Max-Age=2592000", "Path=/", "Secure", "HttpOnly", "SameSite=Lax");
    }

    @Test
    void withoutTheAttributeTheCookieLastsTheBrowserSession() {
        serializer.writeCookieValue(new CookieValue(request, response, "session-id"));

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("__Host-sid=")
                .contains("Path=/", "Secure", "HttpOnly", "SameSite=Lax")
                .doesNotContain("Max-Age");
    }

    @Test
    void emptyValueExpiresTheCookieEvenWhenPersistent() {
        request.setAttribute(PersistentSessionCookieSerializer.PERSISTENT, Boolean.TRUE);

        serializer.writeCookieValue(new CookieValue(request, response, ""));

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("__Host-sid=;")
                .contains("Max-Age=0");
    }

    @Test
    void readsTheSessionIdBack() {
        request.setCookies(new Cookie("__Host-sid", Base64.getEncoder().encodeToString("session-id".getBytes(UTF_8))));

        assertThat(serializer.readCookieValues(request)).containsExactly("session-id");
    }
}
