package br.com.liberdadefamilia.markets.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/** A link a family may attach to a market: https only, with a host, no credentials, within a length. */
public final class HttpsLink {

    private HttpsLink() {}

    public static Optional<URI> parse(String raw, int maxLength) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.strip();
        if (text.isEmpty() || text.length() > maxLength) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(text);
            boolean ok = "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getRawUserInfo() == null;
            return ok ? Optional.of(uri) : Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
