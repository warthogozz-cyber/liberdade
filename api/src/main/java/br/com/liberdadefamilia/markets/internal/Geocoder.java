package br.com.liberdadefamilia.markets.internal;

import java.util.List;

/** Turns the text of an address into places. */
public interface Geocoder {

    /** Up to 5 places for the query, best first. The query is never logged. */
    List<GeocodedPlace> search(String query) throws GeocodingException;
}
