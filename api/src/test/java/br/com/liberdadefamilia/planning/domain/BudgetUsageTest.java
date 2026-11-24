package br.com.liberdadefamilia.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BudgetUsageTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void prdEightyPercent() {
        BudgetUsage usage = BudgetUsage.of(bd("960"), bd("1200"));

        assertThat(usage.percent()).isEqualTo(80);
        assertThat(usage.state()).isEqualTo(BudgetState.NEAR);
        assertThat(usage.overBy()).isNull();
    }

    @Test
    void prdOverBySixty() {
        BudgetUsage usage = BudgetUsage.of(bd("1260"), bd("1200"));

        assertThat(usage.percent()).isEqualTo(105);
        assertThat(usage.state()).isEqualTo(BudgetState.OVER);
        assertThat(usage.overBy()).isEqualByComparingTo("60.00");
    }

    @Test
    void exactlyAtLimit() {
        BudgetUsage usage = BudgetUsage.of(bd("1200"), bd("1200"));

        assertThat(usage.percent()).isEqualTo(100);
        assertThat(usage.state()).isEqualTo(BudgetState.AT);
        assertThat(usage.overBy()).isNull();
    }

    @Test
    void underIsUnder() {
        BudgetUsage usage = BudgetUsage.of(bd("959.99"), bd("1200"));

        assertThat(usage.percent()).isEqualTo(79);
        assertThat(usage.state()).isEqualTo(BudgetState.UNDER);
    }
}
