package br.com.liberdadefamilia.markets.internal;

/** A coordinate that is not a number, or is outside the globe. Never carries the value. */
public final class LocationInvalidException extends RuntimeException {

    public LocationInvalidException() {
        super("location is not a point on the globe");
    }
}
