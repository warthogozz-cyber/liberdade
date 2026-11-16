package br.com.liberdadefamilia.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.shared.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Property tests with a fixed seed (see ADR-0013): 1000 cases per property. */
class CategoryRankingPropertiesTest {

    private static final long SEED = 20261102L;
    private static final int CASES = 1000;
    private static final int LIMIT = 6;
    private static final Currency BRL = Currency.getInstance("BRL");

    @ParameterizedTest
    @MethodSource("totals")
    void sumsAreKept(List<CategoryTotal> totals) {
        var ranked = CategoryRanking.rank(totals, LIMIT);

        assertThat(sum(ranked.stream().map(RankedCategory::current).toList()))
                .isEqualTo(sum(totals.stream().map(CategoryTotal::current).toList()));
        assertThat(sum(ranked.stream().map(RankedCategory::previous).toList()))
                .isEqualTo(sum(totals.stream().map(CategoryTotal::previous).toList()));
    }

    @ParameterizedTest
    @MethodSource("totals")
    void atMostSevenLines(List<CategoryTotal> totals) {
        assertThat(CategoryRanking.rank(totals, LIMIT).size()).isLessThanOrEqualTo(LIMIT + 1);
    }

    @ParameterizedTest
    @MethodSource("totals")
    void nullIdOnlyLast(List<CategoryTotal> totals) {
        var ranked = CategoryRanking.rank(totals, LIMIT);

        for (int i = 0; i < ranked.size() - 1; i++) {
            assertThat(ranked.get(i).categoryId()).as("line %d", i).isNotNull();
        }
    }

    private static Money sum(List<Money> values) {
        return values.stream().reduce(Money.zero(BRL), Money::plus);
    }

    static Stream<Arguments> totals() {
        var random = new SplittableRandom(SEED);
        return Stream.generate(() -> Arguments.of(totalsOf(random))).limit(CASES);
    }

    private static List<CategoryTotal> totalsOf(SplittableRandom random) {
        int count = random.nextInt(0, 16);
        List<CategoryTotal> totals = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            totals.add(new CategoryTotal(new UUID(random.nextLong(), random.nextLong()), money(random), money(random)));
        }
        return List.copyOf(totals);
    }

    /** Zero one time in four, so that lines with both months at zero show up. */
    private static Money money(SplittableRandom random) {
        long cents = random.nextInt(4) == 0 ? 0 : random.nextLong(1, 10_000_000);
        return Money.of(BigDecimal.valueOf(cents, 2), BRL);
    }
}
