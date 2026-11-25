package br.com.liberdadefamilia.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Same as the untrusted case, but the loopback peer is a trusted proxy (in CIDR form, as the compose network is in
 * production), so its forwarded headers are honoured.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "server.tomcat.remoteip.internal-proxies=127.0.0.1/32, ::1/128",
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
class ForwardedHeadersTrustedIT {

    @LocalServerPort
    int port;

    @Test
    void forwardedForFromTrustedProxyIsUsed() throws Exception {
        var client = new ForwardedHeadersClient(port);
        for (int i = 1; i <= 21; i++) {
            int status = client.postOptions("X-Forwarded-For", "203.0.113." + i).statusCode();
            assertThat(status).as("attempt %d", i).isEqualTo(200);
        }
    }

    @Test
    void httpsFromProxySetsHsts() throws Exception {
        var client = new ForwardedHeadersClient(port);

        assertThat(client.getCsrf("X-Forwarded-Proto", "https").headers().firstValue("Strict-Transport-Security"))
                .isPresent();
        assertThat(client.getCsrf().headers().firstValue("Strict-Transport-Security"))
                .isEmpty();
    }
}
