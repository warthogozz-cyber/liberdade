package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.shared.RateLimiter;
import br.com.liberdadefamilia.support.CsrfSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The sign-in endpoints answer at most 20 attempts a minute per address. The clock stands still at 12:00:15, so every
 * attempt falls in the same minute and the 21st must wait 45 seconds. Each test uses its own address.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginRateLimitIT {

    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");
    private static final String WEB_ORIGIN = "http://localhost:5173";

    @TestConfiguration(proxyBeanMethods = false)
    static class StoppedClock {

        @Bean
        @Primary
        @Qualifier("loginRateLimiter")
        RateLimiter stoppedClockLoginRateLimiter() {
            return new RateLimiter(
                    Clock.fixed(Instant.parse("2026-11-02T12:00:15Z"), ZoneOffset.UTC), 20, Duration.ofMinutes(1));
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Test
    void xhrOverLimitIs429() {
        var csrf = CsrfSession.start(mvc);
        for (int i = 0; i < 20; i++) {
            assertThat(mvc.post()
                            .uri("/webauthn/authenticate/options")
                            .with(from("10.0.0.21"))
                            .with(csrf))
                    .hasStatusOk();
        }

        var limited = mvc.post()
                .uri("/webauthn/authenticate/options")
                .header(HttpHeaders.ORIGIN, WEB_ORIGIN)
                .with(from("10.0.0.21"))
                .with(csrf)
                .exchange();

        assertThat(limited).hasStatus(429).hasContentType(PROBLEM_JSON);
        assertThat(limited).hasHeader(HttpHeaders.RETRY_AFTER, "45");
        assertThat(limited).hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB_ORIGIN);
        assertThat(limited).bodyJson().extractingPath("$.code").isEqualTo("rate.limited");
        assertThat(limited)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Muitas tentativas. Tente de novo em instantes.");
        // The same address is limited on the sign-in itself too: one budget for every sign-in endpoint.
        assertThat(mvc.post().uri("/login/webauthn").with(from("10.0.0.21")).with(csrf))
                .hasStatus(429);
    }

    @Test
    void navigationOverLimitGoesBackToSignIn() {
        for (int i = 0; i < 20; i++) {
            assertThat(mvc.get().uri("/oauth2/authorization/google").with(from("10.0.0.22")))
                    .hasStatus(302)
                    .redirectedUrl()
                    .startsWith("https://accounts.google.com/");
        }

        assertThat(mvc.get().uri("/oauth2/authorization/google").with(from("10.0.0.22")))
                .hasStatus(302)
                .hasRedirectedUrl(WEB_ORIGIN + "/entrar?erro=limite");
        // The callback is not counted (FR-M5-limiter): state and PKCE already protect it, so it answers as usual.
        assertThat(mvc.get().uri("/login/oauth2/code/google").with(from("10.0.0.22")))
                .hasStatus(302)
                .hasRedirectedUrl(WEB_ORIGIN + "/entrar?erro=login");
    }

    @Test
    void otherPathsAreNotLimited() {
        for (int i = 0; i < 25; i++) {
            assertThat(mvc.get().uri("/v1/csrf").with(from("10.0.0.23"))).hasStatusOk();
            assertThat(mvc.get().uri("/login/oauth2/code/google").with(from("10.0.0.23")))
                    .hasStatus(302)
                    .hasRedirectedUrl(WEB_ORIGIN + "/entrar?erro=login");
        }
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
