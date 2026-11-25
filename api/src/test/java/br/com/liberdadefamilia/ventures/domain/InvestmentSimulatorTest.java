package br.com.liberdadefamilia.ventures.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvestmentSimulatorTest {

    private static InvestmentScenario scenario(String name, String percent) {
        return new InvestmentScenario(name, new BigDecimal(percent));
    }

    private static List<InvestmentOutcome> run(String initial, String monthly, int years, InvestmentScenario... s) {
        return InvestmentSimulator.simulate(new BigDecimal(initial), new BigDecimal(monthly), years, List.of(s));
    }

    @Test
    void zeroRate() {
        InvestmentOutcome o = run("1000", "100", 1, scenario("a", "0")).get(0);

        assertThat(o.finalBalance().toPlainString()).isEqualTo("2200.00");
        assertThat(o.totalContributed().toPlainString()).isEqualTo("2200.00");
        assertThat(o.earnings().toPlainString()).isEqualTo("0.00");
    }

    @Test
    void oneScenario() {
        InvestmentOutcome o = run("1000", "100", 1, scenario("a", "12")).get(0);

        assertThat(o.finalBalance().toPlainString()).isEqualTo("2384.65");
        assertThat(o.totalContributed().toPlainString()).isEqualTo("2200.00");
        assertThat(o.earnings().toPlainString()).isEqualTo("184.65");
    }

    @Test
    void threeScenarios() {
        List<InvestmentOutcome> out =
                run("5000", "200", 5, scenario("low", "6"), scenario("mid", "10.5"), scenario("none", "0"));

        assertThat(out).extracting(InvestmentOutcome::name).containsExactly("low", "mid", "none");
        assertThat(out)
                .extracting(o -> o.finalBalance().toPlainString())
                .containsExactly("20588.29", "23735.37", "17000.00");
    }

    @Test
    void lumpSumOnly() {
        InvestmentOutcome o = run("10000", "0", 10, scenario("a", "15")).get(0);

        assertThat(o.finalBalance().toPlainString()).isEqualTo("40455.58");
    }

    @Test
    void fiftyYearsAtTenPercent() {
        long start = System.nanoTime();
        InvestmentOutcome o = run("0", "500", 50, scenario("a", "10")).get(0);
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(o.finalBalance().toPlainString()).isEqualTo("7298018.76");
        assertThat(o.totalContributed().toPlainString()).isEqualTo("300000.00");
        assertThat(millis).isLessThan(200);
    }
}
