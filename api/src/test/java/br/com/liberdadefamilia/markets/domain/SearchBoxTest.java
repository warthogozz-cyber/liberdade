package br.com.liberdadefamilia.markets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SearchBoxTest {

    private static GeoPoint at(String lat, String lon) {
        return new GeoPoint(new BigDecimal(lat), new BigDecimal(lon));
    }

    @Test
    void threeKmAtTheEquator() {
        SearchBox box = SearchBox.around(at("0", "0"), 3);

        assertThat(box.maxLat()).isEqualByComparingTo("0.03593");
        assertThat(box.minLat()).isEqualByComparingTo("-0.03593");
        assertThat(box.maxLon()).isEqualByComparingTo("0.03593");
        assertThat(box.minLon()).isEqualByComparingTo("-0.03593");
    }

    @Test
    void widerAwayFromTheEquator() {
        SearchBox box = SearchBox.around(at("-30.00", "-50.00"), 3);

        assertThat(box.maxLon()).isEqualByComparingTo("-49.95851");
        assertThat(box.minLon()).isEqualByComparingTo("-50.04149");
        assertThat(box.maxLat()).isEqualByComparingTo("-29.96407");
        assertThat(box.minLat()).isEqualByComparingTo("-30.03593");
    }

    @Test
    void cosineFloorNearPole() {
        SearchBox box = SearchBox.around(at("89.99", "10"), 3);

        // cos(89.99 degrees) is 0.000174, floored at 0.01: 4 / (111.32 * 0.01) = 3.59324
        assertThat(box.maxLon()).isEqualByComparingTo("13.59324");
        assertThat(box.minLon()).isEqualByComparingTo("6.40676");
        assertThat(box.maxLat()).isEqualByComparingTo("90");
    }

    @Test
    void staysInsideTheGlobe() {
        SearchBox box = SearchBox.around(at("89.99", "179.99"), 3);

        assertThat(box.maxLat()).isEqualByComparingTo("90");
        assertThat(box.maxLon()).isEqualByComparingTo("180");
        assertThat(box.minLon()).isGreaterThanOrEqualTo(new BigDecimal("-180"));
    }

    @Test
    void radiusPlusOneKm() {
        SearchBox box = SearchBox.around(at("0", "0"), 10);

        assertThat(box.maxLat()).isEqualByComparingTo("0.09881"); // 11 / 111.32
    }
}
