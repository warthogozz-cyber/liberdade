package br.com.liberdadefamilia.billing.internal;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How selling is set up. The defaults in {@code application.properties} are the safe ones: nothing on sale.
 *
 * @param gateway {@code asaas} (the real one) or {@code fake} (dev, e2e and tests; refused under the prod profile)
 * @param checkoutHosts the hosts a checkout link may point to
 * @param webhookToken the token the gateway sends with each event; empty never authenticates
 */
@ConfigurationProperties("liberdade.billing")
public record BillingProperties(
        boolean enabled,
        boolean presaleOpen,
        String gateway,
        List<String> checkoutHosts,
        String webhookToken,
        Asaas asaas) {

    public BillingProperties {
        checkoutHosts = checkoutHosts == null ? List.of() : List.copyOf(checkoutHosts);
        webhookToken = webhookToken == null ? "" : webhookToken;
        gateway =
                gateway == null || gateway.isBlank() ? "asaas" : gateway.strip().toLowerCase(Locale.ROOT);
        asaas = asaas == null ? new Asaas("", "") : asaas;
    }

    /** True when the gateway is the fake one. */
    public boolean fakeGateway() {
        return "fake".equals(gateway);
    }

    /** The Asaas account to talk to. */
    public record Asaas(String baseUrl, String apiKey) {

        public Asaas {
            baseUrl = baseUrl == null ? "" : baseUrl;
            apiKey = apiKey == null ? "" : apiKey;
        }
    }
}
