package br.com.liberdadefamilia.markets.domain;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** An Instagram profile or post, always written back as its canonical https URL without query or fragment. */
public sealed interface InstagramRef permits InstagramRef.Profile, InstagramRef.Post, InstagramRef.Reel {

    Pattern USERNAME = Pattern.compile("[A-Za-z0-9._]{1,30}");
    Pattern SHORTCODE = Pattern.compile("[A-Za-z0-9_-]{1,40}");
    String ORIGIN = "https://www.instagram.com/";

    String canonicalUrl();

    /** A profile, by its username in lowercase. */
    record Profile(String username) implements InstagramRef {
        @Override
        public String canonicalUrl() {
            return ORIGIN + username + "/";
        }
    }

    /** A post, by its shortcode. */
    record Post(String shortcode) implements InstagramRef {
        @Override
        public String canonicalUrl() {
            return ORIGIN + "p/" + shortcode + "/";
        }
    }

    /** A reel, by its shortcode. It embeds like a post. */
    record Reel(String shortcode) implements InstagramRef {
        @Override
        public String canonicalUrl() {
            return ORIGIN + "reel/" + shortcode + "/";
        }
    }

    /** The profile or post named by this text ({@code @user}, {@code user} or an instagram.com URL), or empty. */
    static Optional<InstagramRef> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.strip();
        if (text.isEmpty() || text.length() > 300) {
            return Optional.empty();
        }
        if (text.contains("/") || text.contains(":")) {
            return fromUrl(text);
        }
        return profile(text.startsWith("@") ? text.substring(1) : text);
    }

    private static Optional<InstagramRef> fromUrl(String text) {
        URI uri;
        try {
            uri = new URI(text);
        } catch (java.net.URISyntaxException e) {
            return Optional.empty();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getRawUserInfo() != null
                || uri.getPort() != -1
                || !(host.equals("instagram.com") || host.equals("www.instagram.com"))
                || uri.getPath() == null) {
            return Optional.empty();
        }
        String[] segments = uri.getPath().replaceAll("^/+|/+$", "").split("/");
        if (segments.length == 1) {
            return profile(segments[0]);
        }
        if (segments.length == 2
                && segments[0].equals("p")
                && SHORTCODE.matcher(segments[1]).matches()) {
            return Optional.of(new Post(segments[1]));
        }
        if (segments.length == 2
                && segments[0].equals("reel")
                && SHORTCODE.matcher(segments[1]).matches()) {
            return Optional.of(new Reel(segments[1]));
        }
        return Optional.empty();
    }

    private static Optional<InstagramRef> profile(String username) {
        return USERNAME.matcher(username).matches()
                ? Optional.of(new Profile(username.toLowerCase(Locale.ROOT)))
                : Optional.empty();
    }
}
