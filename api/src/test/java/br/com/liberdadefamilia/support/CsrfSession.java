package br.com.liberdadefamilia.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * A session and its CSRF token, fetched from {@code GET /v1/csrf} and sent back the way the web app sends them: the
 * session cookie plus the token in the header the endpoint names.
 *
 * <p>Full-context tests use this instead of spring-security-test's {@code csrf()}, which swaps the CsrfFilter's
 * repository for a default one (header {@code X-CSRF-TOKEN}) in the cached context and breaks the tests that read the
 * real token.
 */
public record CsrfSession(Cookie cookie, String headerName, String token) implements RequestPostProcessor {

    public static final String SESSION_COOKIE = "__Host-sid";

    /**
     * Opens a session with {@code GET /v1/csrf}, after applying the given post-processors (to sign in, say). When they
     * send a live session cookie, the server keeps that session and sets no cookie, so this one is used.
     */
    public static CsrfSession start(MockMvcTester mvc, RequestPostProcessor... postProcessors) {
        var request = mvc.get().uri("/v1/csrf");
        for (RequestPostProcessor postProcessor : postProcessors) {
            request.with(postProcessor);
        }
        MvcTestResult result = request.exchange();
        assertThat(result).hasStatusOk();
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
        if (cookie == null) {
            cookie = sentCookie(result.getRequest().getCookies());
        }
        assertThat(cookie).isNotNull();
        String body;
        try {
            body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        return new CsrfSession(cookie, JsonPath.read(body, "$.headerName"), JsonPath.read(body, "$.token"));
    }

    private static Cookie sentCookie(Cookie[] cookies) {
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(sent -> SESSION_COOKIE.equals(sent.getName()))
                .findFirst()
                .orElse(null);
    }

    /** The id of this session in the {@code spring_session} table. */
    public String sessionId() {
        return sessionIdOf(cookie);
    }

    /** Spring Session writes the session id Base64-encoded in the cookie. */
    public static String sessionIdOf(Cookie cookie) {
        return new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
    }

    @Override
    public MockHttpServletRequest postProcessRequest(MockHttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            request.setCookies(cookie);
        } else {
            Cookie[] withSession = Arrays.copyOf(cookies, cookies.length + 1);
            withSession[cookies.length] = cookie;
            request.setCookies(withSession);
        }
        request.addHeader(headerName, token);
        return request;
    }
}
