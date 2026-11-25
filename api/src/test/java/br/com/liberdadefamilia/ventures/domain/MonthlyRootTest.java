package br.com.liberdadefamilia.ventures.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import org.junit.jupiter.api.Test;

class MonthlyRootTest {

    @Test
    void twelvePercent() {
        BigDecimal r = MonthlyRoot.of(new BigDecimal("12"));

        assertThat(r.toPlainString()).startsWith("0.00948879293458297");
        BigDecimal compounded = BigDecimal.ONE.add(r).pow(12, new MathContext(40));
        assertThat(compounded.subtract(new BigDecimal("1.12")).abs()).isLessThan(new BigDecimal("1E-18"));
    }

    @Test
    void zeroIsExact() {
        assertThat(MonthlyRoot.of(BigDecimal.ZERO).toPlainString()).isEqualTo("0");
    }
}
