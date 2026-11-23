package br.com.liberdadefamilia.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ReserveCoverageTest {

    @Test
    void prdFourMonths() {
        ReserveCoverage coverage = ReserveCoverage.of(new BigDecimal("18000"), new BigDecimal("13500"), 6);

        assertThat(coverage.months()).isEqualByComparingTo("4.0");
        assertThat(coverage.missing()).isEqualByComparingTo("9000.00");
        assertThat(coverage.average()).isEqualByComparingTo("4500.00");
    }

    @Test
    void floorsToOneDecimal() {
        ReserveCoverage coverage = ReserveCoverage.of(new BigDecimal("10000"), new BigDecimal("9000"), 6);

        assertThat(coverage.months()).isEqualTo(new BigDecimal("3.3"));
    }

    @Test
    void missingNeverNegative() {
        ReserveCoverage coverage = ReserveCoverage.of(new BigDecimal("50000"), new BigDecimal("13500"), 6);

        assertThat(coverage.missing()).isEqualByComparingTo("0");
    }

    @Test
    void withoutCostIsEmpty() {
        ReserveCoverage coverage = ReserveCoverage.of(new BigDecimal("50000"), null, 6);

        assertThat(coverage.months()).isNull();
        assertThat(coverage.missing()).isNull();
        assertThat(coverage.average()).isNull();
    }
}
