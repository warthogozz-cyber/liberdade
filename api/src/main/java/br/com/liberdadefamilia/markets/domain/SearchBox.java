package br.com.liberdadefamilia.markets.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * The bounding box of a search: {@code radiusKm + 1} km around the center, clamped to the globe (it never wraps over
 * the antimeridian or a pole). The width grows away from the equator by {@code 1 / cos(lat)}, with the cosine never
 * below 0.01, the same as {@code cos(radians(lat))} in SQL.
 */
public record SearchBox(BigDecimal minLat, BigDecimal maxLat, BigDecimal minLon, BigDecimal maxLon) {

    private static final BigDecimal KM_PER_DEGREE = new BigDecimal("111.32");
    private static final double MIN_COSINE = 0.01;
    private static final BigDecimal MAX_LAT = BigDecimal.valueOf(90);
    private static final BigDecimal MAX_LON = BigDecimal.valueOf(180);
    private static final int SCALE = 5;

    public static SearchBox around(GeoPoint center, int radiusKm) {
        BigDecimal km = BigDecimal.valueOf(radiusKm + 1L);
        BigDecimal halfHeight = km.divide(KM_PER_DEGREE, MathContext.DECIMAL64);
        double cosine =
                Math.max(MIN_COSINE, Math.cos(Math.toRadians(center.lat().doubleValue())));
        BigDecimal halfWidth = halfHeight.divide(BigDecimal.valueOf(cosine), MathContext.DECIMAL64);
        return new SearchBox(
                clamp(center.lat().subtract(halfHeight), MAX_LAT),
                clamp(center.lat().add(halfHeight), MAX_LAT),
                clamp(center.lon().subtract(halfWidth), MAX_LON),
                clamp(center.lon().add(halfWidth), MAX_LON));
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal limit) {
        return value.max(limit.negate()).min(limit).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
