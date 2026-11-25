package br.com.liberdadefamilia.markets.internal;

/** The lookup failed: an error, a timeout or an answer that is not what the service sends. Never carries the query. */
public final class GeocodingException extends Exception {

    public GeocodingException(String message) {
        super(message);
    }
}
