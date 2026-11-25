package br.com.liberdadefamilia.markets.internal;

import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the Feira collects its outside data (D2).
 *
 * @param botUrl the public page that describes the robot; it goes in every {@code User-Agent}
 * @param osm the weekly OpenStreetMap extract
 * @param geocoding the address lookup
 * @param instagram the Instagram embed
 */
@ConfigurationProperties("liberdade.markets")
public record MarketsProperties(String botUrl, Osm osm, Geocoding geocoding, Instagram instagram) {

    /** Where the extract the workflow publishes lives: the release of this repository. */
    static final String DEFAULT_DATASET_URL =
            "https://github.com/warthogozz-cyber/liberdade/releases/download/osm-markets-data";

    public MarketsProperties {
        botUrl = botUrl == null ? "" : botUrl;
        osm = osm == null ? new Osm(null, null, null, null, null, null) : osm;
        geocoding = geocoding == null ? new Geocoding(null, null, null, null, null, null) : geocoding;
        instagram = instagram == null ? new Instagram(null, null, null, null, null) : instagram;
    }

    /** True when any of the three sources is the fake one. */
    public boolean anyFake() {
        return osm.fake() || geocoding.fake() || instagram.fake();
    }

    /**
     * The extract.
     *
     * @param source {@code github} or {@code fake} (dev, e2e and tests; refused under the prod profile)
     * @param datasetUrl the folder that holds {@code manifest.json} and {@code markets-br.csv.gz}
     * @param timeout connection and read timeout
     * @param maxBytes the download is cut when it is larger than this
     * @param minRowRatio the smallest share of the last import's rows a new file may have
     * @param staleAfter how old the last import may be before {@code DATASET_STALE}
     */
    public record Osm(
            String source,
            String datasetUrl,
            Duration timeout,
            Long maxBytes,
            Double minRowRatio,
            Duration staleAfter) {

        public Osm {
            source = normalise(source, "github");
            datasetUrl = datasetUrl == null || datasetUrl.isBlank() ? DEFAULT_DATASET_URL : stripSlash(datasetUrl);
            timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
            maxBytes = maxBytes == null ? 52_428_800L : maxBytes;
            minRowRatio = minRowRatio == null ? 0.8 : minRowRatio;
            staleAfter = staleAfter == null ? Duration.ofDays(15) : staleAfter;
        }

        public boolean fake() {
            return "fake".equals(source);
        }
    }

    /**
     * The address lookup.
     *
     * @param enabled the switch
     * @param source {@code nominatim} or {@code fake}
     * @param baseUrl the Nominatim address
     * @param minInterval the least time between two calls of one process
     * @param dailyLimit lookups per family per day
     * @param timeout connection and read timeout
     */
    public record Geocoding(
            Boolean enabled,
            String source,
            String baseUrl,
            Duration minInterval,
            Integer dailyLimit,
            Duration timeout) {

        public Geocoding {
            enabled = enabled == null || enabled;
            source = normalise(source, "nominatim");
            baseUrl =
                    baseUrl == null || baseUrl.isBlank() ? "https://nominatim.openstreetmap.org" : stripSlash(baseUrl);
            minInterval = minInterval == null ? Duration.ofSeconds(2) : minInterval;
            dailyLimit = dailyLimit == null ? 20 : dailyLimit;
            timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        }

        public boolean fake() {
            return "fake".equals(source);
        }
    }

    /**
     * The Instagram embed.
     *
     * @param enabled the switch of "Ver aqui"
     * @param source {@code meta} or {@code fake}
     * @param baseUrl the Graph API address
     * @param timeout connection and read timeout
     * @param profileEmbeds whether profile links are embedded too (a profile cannot be embedded: Instagram refuses it without a token, so this stays off)
     */
    public record Instagram(Boolean enabled, String source, String baseUrl, Duration timeout, Boolean profileEmbeds) {

        public Instagram {
            enabled = enabled == null || enabled;
            source = normalise(source, "meta");
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://graph.facebook.com/v25.0" : stripSlash(baseUrl);
            timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
            profileEmbeds = profileEmbeds != null && profileEmbeds;
        }

        public boolean fake() {
            return "fake".equals(source);
        }
    }

    private static String normalise(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip().toLowerCase(Locale.ROOT);
    }

    private static String stripSlash(String url) {
        String stripped = url.strip();
        return stripped.endsWith("/") ? stripped.substring(0, stripped.length() - 1) : stripped;
    }
}
