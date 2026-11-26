package br.com.liberdadefamilia.markets.internal;

import java.util.Optional;

/** The embed of dev, e2e and the tests: an invented blockquote, and no network. A URL with "privado" is refused. */
final class FakeInstagramOembed implements InstagramOembed {

    @Override
    public Optional<String> html(String canonicalUrl) {
        if (canonicalUrl.contains("privado")) {
            return Optional.empty();
        }
        return Optional.of("<blockquote class=\"instagram-media\" data-instgrm-permalink=\"" + canonicalUrl
                + "\"><a href=\"" + canonicalUrl + "\">Ver no Instagram</a></blockquote>");
    }
}
