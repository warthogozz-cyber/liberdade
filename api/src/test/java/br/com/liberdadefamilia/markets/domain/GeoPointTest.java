package br.com.liberdadefamilia.markets.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GeoPointTest {

    @Test
    void rejectsMoreDecimals() {
        GeoPoint tooPrecise = new GeoPoint(new BigDecimal("-10.123"), new BigDecimal("-50.1"));

        assertThatThrownBy(() -> tooPrecise.requireScale(2)).isInstanceOf(LocationTooPreciseException.class);
        assertThatCode(() -> new GeoPoint(new BigDecimal("-10.10"), new BigDecimal("-50.1")).requireScale(2))
                .doesNotThrowAnyException();
        assertThatCode(() -> new GeoPoint(new BigDecimal("-10.1"), new BigDecimal("-50.100")).requireScale(2))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsLongitudeWithMoreDecimals() {
        GeoPoint tooPrecise = new GeoPoint(new BigDecimal("-10.1"), new BigDecimal("-50.123"));

        assertThatThrownBy(() -> tooPrecise.requireScale(2)).isInstanceOf(LocationTooPreciseException.class);
    }

    @Test
    void rejectsOutOfRange() {
        assertThatThrownBy(() -> new GeoPoint(new BigDecimal("90.01"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(BigDecimal.ZERO, new BigDecimal("-180.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new GeoPoint(new BigDecimal("-90"), new BigDecimal("180")))
                .doesNotThrowAnyException();
    }

    @Test
    void roundsHalfUp() {
        GeoPoint rounded = new GeoPoint(new BigDecimal("-10.1255"), new BigDecimal("50.1245")).rounded(3);

        assertThat(rounded.lat()).isEqualByComparingTo("-10.126");
        assertThat(rounded.lon()).isEqualByComparingTo("50.125");
        assertThat(rounded.lat().scale()).isEqualTo(3);
    }
}
