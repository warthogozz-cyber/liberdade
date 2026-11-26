package br.com.liberdadefamilia.markets.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** A point on the globe: latitude in [-90, 90], longitude in [-180, 180], as exact decimals. */
public record GeoPoint(BigDecimal lat, BigDecimal lon) {

    private static final BigDecimal MAX_LAT = BigDecimal.valueOf(90);
    private static final BigDecimal MAX_LON = BigDecimal.valueOf(180);

    public GeoPoint {
        Objects.requireNonNull(lat, "lat");
        Objects.requireNonNull(lon, "lon");
        if (lat.abs().compareTo(MAX_LAT) > 0) {
            throw new IllegalArgumentException("latitude out of range");
        }
        if (lon.abs().compareTo(MAX_LON) > 0) {
            throw new IllegalArgumentException("longitude out of range");
        }
    }

    /** This point when neither coordinate has more than {@code maxDecimals} decimals (trailing zeros do not count). */
    public GeoPoint requireScale(int maxDecimals) {
        if (lat.stripTrailingZeros().scale() > maxDecimals
                || lon.stripTrailingZeros().scale() > maxDecimals) {
            throw new LocationTooPreciseException(maxDecimals);
        }
        return this;
    }

    /** The same point with exactly {@code decimals} decimals, rounded half up. */
    public GeoPoint rounded(int decimals) {
        return new GeoPoint(lat.setScale(decimals, RoundingMode.HALF_UP), lon.setScale(decimals, RoundingMode.HALF_UP));
    }
}
