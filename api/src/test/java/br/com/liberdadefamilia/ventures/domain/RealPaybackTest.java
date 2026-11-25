package br.com.liberdadefamilia.ventures.domain;

import static br.com.liberdadefamilia.ventures.domain.ActualVsPlannedSupport.active;
import static br.com.liberdadefamilia.ventures.domain.ActualVsPlannedSupport.month;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RealPaybackTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    void paidBackInThirdMonth() {
        RealPayback real = active(
                        month("2026-09", "300", "800"), month("2026-10", "300", "0"), month("2026-11", "300", "0"))
                .real();

        assertThat(real.state()).isEqualTo(RealPaybackState.PAID_BACK);
        assertThat(real.months()).isEqualTo(3);
        assertThat(real.investment()).isEqualTo(d("500.00"));
        assertThat(real.missing()).isNull();
    }

    @Test
    void notYet() {
        RealPayback real = active(month("2026-09", "0", "800"), month("2026-10", "300", "0"))
                .real();

        assertThat(real.state()).isEqualTo(RealPaybackState.NOT_YET);
        assertThat(real.missing()).isEqualTo(d("500.00"));
        assertThat(real.investment()).isEqualTo(d("800.00"));
        assertThat(real.months()).isNull();
    }

    @Test
    void neverNegative() {
        RealPayback real = active(month("2026-09", "100", "0"), month("2026-10", "200", "0"))
                .real();

        assertThat(real.state()).isEqualTo(RealPaybackState.NEVER_NEGATIVE);
        assertThat(real.investment()).isNull();
        assertThat(real.months()).isNull();
        assertThat(real.missing()).isNull();
    }

    @Test
    void noData() {
        ActualVsPlannedResult result = active();

        assertThat(result.real().state()).isEqualTo(RealPaybackState.NO_DATA);
        assertThat(result.real().investment()).isNull();
        assertThat(result.months()).hasSize(3);
        assertThat(result.months()).allSatisfy(row -> {
            assertThat(row.income()).isEqualTo(d("0.00"));
            assertThat(row.expense()).isEqualTo(d("0.00"));
            assertThat(row.plannedResult()).isEqualTo(d("540.00"));
        });
    }

    @Test
    void lowestPointIsTheInvestment() {
        // results -300, -200, +200, +900 -> running -300, -500, -300, +600
        RealPayback real = ActualVsPlannedSupport.compute(
                        java.time.LocalDate.of(2026, 8, 1),
                        null,
                        VentureStatus.ACTIVE,
                        java.time.YearMonth.of(2026, 11),
                        month("2026-08", "0", "300"),
                        month("2026-09", "0", "200"),
                        month("2026-10", "200", "0"),
                        month("2026-11", "900", "0"))
                .real();

        assertThat(real.state()).isEqualTo(RealPaybackState.PAID_BACK);
        assertThat(real.investment()).isEqualTo(d("500.00"));
        assertThat(real.months()).isEqualTo(4);
    }

    @Test
    void negativeAgainAfterPaidBackStaysPaid() {
        // -100, +200, -300 -> running -100, +100, -200
        RealPayback real = active(
                        month("2026-09", "0", "100"), month("2026-10", "200", "0"), month("2026-11", "0", "300"))
                .real();

        assertThat(real.state()).isEqualTo(RealPaybackState.PAID_BACK);
        assertThat(real.months()).isEqualTo(2);
        assertThat(real.investment()).isEqualTo(d("200.00"));
    }

    @Test
    void emptyMonthsCountInPayback() {
        RealPayback real = active(month("2026-09", "0", "100"), month("2026-11", "200", "0"))
                .real();

        assertThat(real.state()).isEqualTo(RealPaybackState.PAID_BACK);
        assertThat(real.months()).isEqualTo(3);
    }
}
