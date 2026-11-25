package br.com.liberdadefamilia.markets.domain;

/** A location with more decimals than the place for it allows (the family never shares a finer position). */
public class LocationTooPreciseException extends RuntimeException {

    public LocationTooPreciseException(int maxDecimals) {
        super("location has more than " + maxDecimals + " decimals");
    }
}
