package br.com.liberdadefamilia.indicators.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ValueRangeTest {

    @Test
    void ratesBetweenMinusFiveAndHundred() {
        for (IndicatorUnit unit :
                new IndicatorUnit[] {IndicatorUnit.PERCENT_YEAR, IndicatorUnit.PERCENT_MONTH, IndicatorUnit.PERCENT_12M
                }) {
            assertThat(ValueRange.accepts(unit, new BigDecimal("-5"))).isTrue();
            assertThat(ValueRange.accepts(unit, new BigDecimal("100"))).isTrue();
            assertThat(ValueRange.accepts(unit, new BigDecimal("-5.01"))).isFalse();
            assertThat(ValueRange.accepts(unit, new BigDecimal("100.01"))).isFalse();
        }
    }

    @Test
    void dollarBetweenOneAndFifty() {
        assertThat(ValueRange.accepts(IndicatorUnit.BRL, new BigDecimal("1"))).isTrue();
        assertThat(ValueRange.accepts(IndicatorUnit.BRL, new BigDecimal("50"))).isTrue();
        assertThat(ValueRange.accepts(IndicatorUnit.BRL, new BigDecimal("0.99")))
                .isFalse();
        assertThat(ValueRange.accepts(IndicatorUnit.BRL, new BigDecimal("50.01")))
                .isFalse();
    }
}
