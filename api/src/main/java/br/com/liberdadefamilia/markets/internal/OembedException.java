package br.com.liberdadefamilia.markets.internal;

/** The embed service failed: an error, a timeout or a bad answer. Never carries the address that was asked. */
public final class OembedException extends Exception {

    public OembedException(String message) {
        super(message);
    }
}
