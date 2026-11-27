package br.com.liberdadefamilia.markets.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Totals the shopping list at each chosen market. Only the items that can be compared fairly count: priced at every
 * chosen market, in the same unit. Money stays in {@link BigDecimal} and is rounded once, at the end.
 */
public final class ComparisonCalculator {

    private static final int TOTAL_SCALE = 2;

    private ComparisonCalculator() {}

    /** A list item: its quantity and the last price at the markets that have one. */
    public record ItemPrices(UUID productId, BigDecimal quantity, Map<UUID, PricePoint> byMarket) {}

    /** The last price of a product at a market. */
    public record PricePoint(BigDecimal unitPrice, ProductUnit unit, LocalDate observedOn) {}

    /**
     * {@code totals} and {@code missing} have an entry for every chosen market; {@code common} and
     * {@code unitMismatch} hold product ids.
     */
    public record Comparison(
            Map<UUID, BigDecimal> totals,
            Map<UUID, Integer> missing,
            int commonCount,
            Set<UUID> common,
            Set<UUID> unitMismatch) {}

    /**
     * Compares the items at the markets. With no market, nothing is common.
     *
     * @param items the list, with the prices each product has
     * @param markets the chosen markets, in the order to answer
     */
    public static Comparison compute(List<ItemPrices> items, List<UUID> markets) {
        Map<UUID, BigDecimal> sums = new LinkedHashMap<>();
        Map<UUID, Integer> missing = new LinkedHashMap<>();
        for (UUID market : markets) {
            sums.put(market, BigDecimal.ZERO);
            missing.put(market, 0);
        }
        Set<UUID> common = new LinkedHashSet<>();
        Set<UUID> mismatch = new LinkedHashSet<>();
        if (!markets.isEmpty()) {
            for (ItemPrices item : items) {
                boolean complete = true;
                Set<ProductUnit> units = new HashSet<>();
                for (UUID market : markets) {
                    PricePoint point = item.byMarket().get(market);
                    if (point == null) {
                        complete = false;
                        missing.merge(market, 1, Integer::sum);
                    } else {
                        units.add(point.unit());
                    }
                }
                if (!complete) {
                    continue;
                }
                if (units.size() > 1) {
                    mismatch.add(item.productId());
                    continue;
                }
                common.add(item.productId());
                for (UUID market : markets) {
                    BigDecimal line = item.byMarket().get(market).unitPrice().multiply(item.quantity());
                    sums.merge(market, line, BigDecimal::add);
                }
            }
        }
        Map<UUID, BigDecimal> totals = new LinkedHashMap<>();
        sums.forEach((market, sum) -> totals.put(market, sum.setScale(TOTAL_SCALE, RoundingMode.HALF_EVEN)));
        return new Comparison(totals, missing, common.size(), common, mismatch);
    }
}
