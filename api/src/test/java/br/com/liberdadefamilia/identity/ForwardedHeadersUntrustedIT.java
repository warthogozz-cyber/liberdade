package br.com.liberdadefamilia.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * With the {@code prod} profile only the compose network is a trusted proxy. The test client connects from loopback,
 * so its {@code X-Forwarded-For} must be ignored and the rate limiter must keep keying on the real peer address.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "liberdade.billing.gateway=asaas",
            "liberdade.indicators.source=bcb",
            "liberdade.markets.osm.source=github",
            "liberdade.markets.geocoding.source=nominatim",
            "liberdade.markets.instagram.source=meta",
            "liberdade.billing.enabled=false",
            // prod logs JSON; left on, it would outlive this context and turn later tests' log lines into JSON.
            "logging.structured.format.console="
        })
@ActiveProfiles({"test", "prod"})
@Import(TestcontainersConfiguration.class)
class ForwardedHeadersUntrustedIT {

    @LocalServerPort
    int port;

    @Test
    void forwardedForFromUntrustedPeerIsIgnored() throws Exception {
        var client = new ForwardedHeadersClient(port);
        for (int i = 1; i <= 20; i++) {
            int status = client.postOptions("X-Forwarded-For", "203.0.113." + i).statusCode();
            // 200 rules out both a CSRF 403 and a limiter 429, so the 429 below can only come from the limiter.
            assertThat(status).as("attempt %d", i).isEqualTo(200);
        }

        assertThat(client.postOptions("X-Forwarded-For", "203.0.113.99").statusCode())
                .isEqualTo(429);
    }

    @Test
    void forwardedProtoFromUntrustedPeerDoesNotEnableHsts() throws Exception {
        var client = new ForwardedHeadersClient(port);

        var response = client.getCsrf("X-Forwarded-Proto", "https");
        // 200 rules out an error page, which would lack the header for the wrong reason.
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
    }
}
