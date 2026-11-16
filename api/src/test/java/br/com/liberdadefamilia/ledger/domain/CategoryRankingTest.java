package br.com.liberdadefamilia.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.shared.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CategoryRankingTest {

    @Test
    void eightCategoriesBecomeSixAndOthers() {
        var totals = new ArrayList<CategoryTotal>();
        for (int i = 1; i <= 8; i++) {
            totals.add(total(i, i * 10 + ".00", i + ".00"));
        }

        var ranked = CategoryRanking.rank(totals, 6);

        assertThat(ranked).hasSize(7);
        assertThat(ranked.get(0).categoryId()).isEqualTo(id(8));
        assertThat(ranked.get(5).categoryId()).isEqualTo(id(3));
        var others = ranked.get(6);
        assertThat(others.categoryId()).isNull();
        assertThat(others.current()).isEqualTo(Money.brl("30.00"));
        assertThat(others.previous()).isEqualTo(Money.brl("3.00"));
    }

    @Test
    void sixCategoriesHaveNoOthers() {
        var totals = new ArrayList<CategoryTotal>();
        for (int i = 1; i <= 6; i++) {
            totals.add(total(i, i + ".00", "0.00"));
        }

        var ranked = CategoryRanking.rank(totals, 6);

        assertThat(ranked).hasSize(6);
        assertThat(ranked).allSatisfy(line -> assertThat(line.categoryId()).isNotNull());
    }

    @Test
    void tieOnCurrentGoesByPreviousThenId() {
        var ranked = CategoryRanking.rank(
                List.of(total(3, "10.00", "1.00"), total(2, "10.00", "5.00"), total(1, "10.00", "1.00")), 6);

        assertThat(ranked).extracting(RankedCategory::categoryId).containsExactly(id(2), id(1), id(3));
    }

    @Test
    void lineWithBothZeroDisappears() {
        var ranked = CategoryRanking.rank(List.of(total(1, "0.00", "0.00"), total(2, "1.00", "0.00")), 6);

        assertThat(ranked).extracting(RankedCategory::categoryId).containsExactly(id(2));
    }

    @Test
    void categoryWithOnlyPreviousAppears() {
        var ranked = CategoryRanking.rank(List.of(total(1, "0.00", "80.00")), 6);

        assertThat(ranked).containsExactly(new RankedCategory(id(1), Money.brl("0.00"), Money.brl("80.00")));
    }

    @Test
    void noTotalsNoLines() {
        assertThat(CategoryRanking.rank(List.of(), 6)).isEmpty();
    }

    private static UUID id(int n) {
        return new UUID(0, n);
    }

    private static CategoryTotal total(int n, String current, String previous) {
        return new CategoryTotal(id(n), Money.brl(current), Money.brl(previous));
    }
}
