package br.com.liberdadefamilia.markets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.markets.domain.ComparisonCalculator.Comparison;
import br.com.liberdadefamilia.markets.domain.ComparisonCalculator.ItemPrices;
import br.com.liberdadefamilia.markets.domain.ComparisonCalculator.PricePoint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ComparisonCalculatorTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    private static PricePoint point(String price, ProductUnit unit) {
        return new PricePoint(new BigDecimal(price), unit, DAY);
    }

    private static ItemPrices item(String quantity, Map<UUID, PricePoint> byMarket) {
        return new ItemPrices(UUID.randomUUID(), new BigDecimal(quantity), byMarket);
    }

    private static Map<UUID, PricePoint> prices(Object... marketAndPrice) {
        Map<UUID, PricePoint> map = new HashMap<>();
        for (int i = 0; i < marketAndPrice.length; i += 2) {
            map.put((UUID) marketAndPrice[i], point((String) marketAndPrice[i + 1], ProductUnit.UN));
        }
        return map;
    }

    @Test
    void onlyCommonItemsCount() {
        ItemPrices one = item("1", prices(A, "5.00", B, "6.00"));
        ItemPrices two = item("1", prices(A, "2.00", B, "1.50"));
        ItemPrices three = item("1", prices(A, "9.00"));

        Comparison result = ComparisonCalculator.compute(List.of(one, two, three), List.of(A, B));

        assertThat(result.totals().get(A)).isEqualByComparingTo("7.00");
        assertThat(result.totals().get(B)).isEqualByComparingTo("7.50");
        assertThat(result.missing().get(B)).isEqualTo(1);
        assertThat(result.missing().get(A)).isZero();
        assertThat(result.commonCount()).isEqualTo(2);
        assertThat(result.common()).containsExactlyInAnyOrder(one.productId(), two.productId());
    }

    @Test
    void multipliesQuantity() {
        List<ItemPrices> items =
                List.of(item("2", prices(A, "4.99", B, "1.00")), item("1.5", prices(A, "10.00", B, "1.00")));

        Comparison result = ComparisonCalculator.compute(items, List.of(A, B));

        assertThat(result.totals().get(A)).isEqualTo(new BigDecimal("24.98"));
    }

    @Test
    void unitMismatchLeftOut() {
        Map<UUID, PricePoint> mixed = Map.of(A, point("5.00", ProductUnit.KG), B, point("4.00", ProductUnit.UN));
        ItemPrices odd = item("1", mixed);
        ItemPrices fine = item("1", prices(A, "1.00", B, "2.00"));

        Comparison result = ComparisonCalculator.compute(List.of(odd, fine), List.of(A, B));

        assertThat(result.unitMismatch()).containsExactly(odd.productId());
        assertThat(result.common()).containsExactly(fine.productId());
        assertThat(result.commonCount()).isEqualTo(1);
        assertThat(result.totals().get(A)).isEqualByComparingTo("1.00");
        assertThat(result.missing().get(A)).isZero();
    }

    @Test
    void roundsOnlyAtTheEnd() {
        List<ItemPrices> items = List.of(
                item("1", prices(A, "0.3333", B, "1")),
                item("1", prices(A, "0.3333", B, "1")),
                item("1", prices(A, "0.3333", B, "1")));

        Comparison result = ComparisonCalculator.compute(items, List.of(A, B));

        assertThat(result.totals().get(A)).isEqualTo(new BigDecimal("1.00"));
    }

    @Test
    void threeMarkets() {
        ItemPrices all = item("1", prices(A, "1.00", B, "2.00", C, "3.00"));
        ItemPrices two = item("1", prices(A, "1.00", B, "2.00"));

        Comparison result = ComparisonCalculator.compute(List.of(all, two), List.of(A, B, C));

        assertThat(result.common()).containsExactly(all.productId());
        assertThat(result.missing().get(C)).isEqualTo(1);
        assertThat(result.totals().get(C)).isEqualByComparingTo("3.00");
    }
}
