package br.com.liberdadefamilia.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * A plain HTTP client against the real Tomcat, so the forwarded-header handling of the servlet container is in play
 * (MockMvc skips it). It keeps the session cookie by hand because the cookie is {@code Secure} and the test talks
 * plain http.
 */
final class ForwardedHeadersClient {

    private final HttpClient http = HttpClient.newHttpClient();
    private final String base;
    private final String cookie;
    private final String csrfHeader;
    private final String csrfToken;

    ForwardedHeadersClient(int port) throws IOException, InterruptedException {
        this.base = "http://127.0.0.1:" + port;
        HttpResponse<String> csrf = http.send(
                HttpRequest.newBuilder(URI.create(base + "/v1/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(csrf.statusCode()).isEqualTo(200);
        String setCookie = csrf.headers().firstValue("Set-Cookie").orElseThrow();
        this.cookie = setCookie.substring(0, setCookie.indexOf(';'));
        this.csrfHeader = JsonPath.read(csrf.body(), "$.headerName");
        this.csrfToken = JsonPath.read(csrf.body(), "$.token");
    }

    /** POST with a valid session and CSRF token, so only the rate limiter can refuse it. */
    HttpResponse<String> postOptions(String... extraHeaders) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(base + "/webauthn/authenticate/options"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("Cookie", cookie)
                .header(csrfHeader, csrfToken);
        for (int i = 0; i < extraHeaders.length; i += 2) {
            request.header(extraHeaders[i], extraHeaders[i + 1]);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> getCsrf(String... extraHeaders) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(base + "/v1/csrf")).GET();
        for (int i = 0; i < extraHeaders.length; i += 2) {
            request.header(extraHeaders[i], extraHeaders[i + 1]);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
