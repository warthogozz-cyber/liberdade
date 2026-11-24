package br.com.liberdadefamilia.indicators.internal;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the indicators are collected.
 *
 * @param source {@code bcb} (the real one) or {@code fake} (dev, e2e and tests; refused under the prod profile)
 * @param botUrl the public page that describes the robot; it goes in the {@code User-Agent}
 * @param timeout connection and read timeout
 * @param retryWaits one wait per new attempt
 * @param pauseBetweenSeries the pause between one series and the next
 */
@ConfigurationProperties("liberdade.indicators")
public record IndicatorProperties(
        String source,
        String sgsBaseUrl,
        String ptaxBaseUrl,
        String botUrl,
        Duration timeout,
        List<Duration> retryWaits,
        Duration pauseBetweenSeries) {

    public IndicatorProperties {
        source = source == null || source.isBlank() ? "bcb" : source.strip().toLowerCase(Locale.ROOT);
        sgsBaseUrl = sgsBaseUrl == null ? "https://api.bcb.gov.br" : sgsBaseUrl;
        ptaxBaseUrl = ptaxBaseUrl == null ? "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1" : ptaxBaseUrl;
        botUrl = botUrl == null ? "" : botUrl;
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        retryWaits =
                retryWaits == null ? List.of(Duration.ofSeconds(2), Duration.ofSeconds(8)) : List.copyOf(retryWaits);
        pauseBetweenSeries = pauseBetweenSeries == null ? Duration.ofSeconds(1) : pauseBetweenSeries;
    }

    /** True when the source is the fake one. */
    public boolean fake() {
        return "fake".equals(source);
    }
}
