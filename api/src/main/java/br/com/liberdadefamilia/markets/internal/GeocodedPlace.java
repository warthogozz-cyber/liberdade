package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.GeoPoint;

/** A place the lookup found: the point already rounded to 3 decimals, and a short label (district and city). */
public record GeocodedPlace(GeoPoint point, String label) {}
