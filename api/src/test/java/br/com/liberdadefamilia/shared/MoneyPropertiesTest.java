package br.com.liberdadefamilia.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Property tests with a fixed seed (see ADR-0013): 1000 cases per property. */
class MoneyPropertiesTest {

    private static final long SEED = 20261102L;
    private static final int CASES = 1000;
    private static final long MAX_CENTS = 100_000_000_000L;

    @ParameterizedTest(name = "[{index}] total={0} parts={1}")
    @MethodSource("totalsAndParts")
    void allocationSumsToTotal(Money total, int parts) {
        var sum = total.allocate(parts).stream().reduce(Money.zero(total.currency()), Money::plus);
        assertThat(sum).isEqualTo(total);
    }

    @ParameterizedTest(name = "[{index}] total={0} parts={1}")
    @MethodSource("totalsAndParts")
    void allocationPartsDifferByAtMostOneCent(Money total, int parts) {
        var allocation = total.allocate(parts);
        var spread = Collections.max(allocation).minus(Collections.min(allocation));
        assertThat(spread).isLessThanOrEqualTo(Money.brl("0.01"));
    }

    @ParameterizedTest(name = "[{index}] a={0} b={1}")
    @MethodSource("pairs")
    void plusThenMinusIsIdentity(Money a, Money b) {
        assertThat(a.plus(b).minus(b)).isEqualTo(a);
    }

    static Stream<Arguments> totalsAndParts() {
        var random = new SplittableRandom(SEED);
        return Stream.generate(() -> Arguments.of(randomMoney(random), random.nextInt(1, 51)))
                .limit(CASES);
    }

    static Stream<Arguments> pairs() {
        var random = new SplittableRandom(SEED);
        return Stream.generate(() -> Arguments.of(randomMoney(random), randomMoney(random)))
                .limit(CASES);
    }

    private static Money randomMoney(SplittableRandom random) {
        long cents = random.nextLong(-MAX_CENTS, MAX_CENTS + 1);
        return Money.brl(BigDecimal.valueOf(cents, 2).toPlainString());
    }
}
