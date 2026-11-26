package br.com.liberdadefamilia.markets.internal;

import java.util.Optional;

/** Turns the address of an Instagram post into the HTML Instagram offers to embed it. */
public interface InstagramOembed {

    /**
     * The embed HTML for the canonical URL, or empty when Instagram refuses it (a profile, a private or missing post)
     * or sends something that is not an embed.
     *
     * @throws OembedException when the service fails: an error, a timeout or an answer that is not JSON
     */
    Optional<String> html(String canonicalUrl) throws OembedException;
}
