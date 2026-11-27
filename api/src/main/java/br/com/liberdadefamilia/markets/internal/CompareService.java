package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.ComparisonCalculator;
import br.com.liberdadefamilia.markets.domain.ComparisonCalculator.Comparison;
import br.com.liberdadefamilia.markets.domain.ComparisonCalculator.ItemPrices;
import br.com.liberdadefamilia.markets.domain.ComparisonCalculator.PricePoint;
import br.com.liberdadefamilia.markets.domain.FamilyRef;
import br.com.liberdadefamilia.markets.domain.MarketRef;
import br.com.liberdadefamilia.markets.domain.OsmRef;
import br.com.liberdadefamilia.markets.domain.ProductUnit;
import br.com.liberdadefamilia.markets.internal.MarketViews.CompareItemView;
import br.com.liberdadefamilia.markets.internal.MarketViews.CompareMarketView;
import br.com.liberdadefamilia.markets.internal.MarketViews.ComparePriceView;
import br.com.liberdadefamilia.markets.internal.MarketViews.CompareView;
import br.com.liberdadefamilia.markets.internal.OsmMarketRepository.OsmObject;
import br.com.liberdadefamilia.markets.internal.PriceRepository.LatestPrice;
import br.com.liberdadefamilia.markets.internal.ShoppingListRepository.ItemRow;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The shopping list at two or three markets (D8). It reads what the list and the receipts hold and writes nothing. */
@Service
class CompareService {

    static final int MIN_MARKETS = 2;
    static final int MAX_MARKETS = 3;
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final int OLD_AFTER_DAYS = 60;

    private final ShoppingListRepository list;
    private final PriceRepository prices;
    private final MarketRepository markets;
    private final OsmMarketRepository osm;
    private final Clock clock;

    CompareService(
            ShoppingListRepository list,
            PriceRepository prices,
            MarketRepository markets,
            OsmMarketRepository osm,
            Clock clock) {
        this.list = list;
        this.prices = prices;
        this.markets = markets;
        this.osm = osm;
        this.clock = clock;
    }

    /** A market the request chose: how it is named in the answer, and the id the calculator tells it by. */
    private record Chosen(String text, UUID id, @Nullable String name) {}

    /**
     * Compares the list at the markets in {@code marketsParam}, in that order.
     *
     * @param marketsParam comma separated refs, or null/blank for none
     * @throws CompareException when there are not two or three different markets
     * @throws MarketNotFoundException when a ref is not one, or is a family id the family does not have
     */
    @Transactional(readOnly = true)
    CompareView compare(UUID workspaceId, @Nullable String marketsParam) {
        List<Chosen> chosen = choose(workspaceId, marketsParam);
        List<ItemRow> rows = list.items(workspaceId);
        Map<UUID, List<LatestPrice>> prices = pricesByProduct(workspaceId);

        List<ItemPrices> items = new ArrayList<>();
        for (ItemRow row : rows) {
            items.add(new ItemPrices(row.productId(), row.quantity(), pointsOf(prices.get(row.productId()), chosen)));
        }
        Comparison comparison = ComparisonCalculator.compute(
                items, chosen.stream().map(Chosen::id).toList());

        LocalDate oldBefore = clock.instant().atZone(SAO_PAULO).toLocalDate().minusDays(OLD_AFTER_DAYS);
        List<CompareItemView> itemViews = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ItemRow row = rows.get(i);
            Map<UUID, PricePoint> byMarket = items.get(i).byMarket();
            List<ComparePriceView> priceViews = new ArrayList<>();
            for (Chosen market : chosen) {
                PricePoint point = byMarket.get(market.id());
                priceViews.add(point == null ? null : priceView(market.text(), point, oldBefore));
            }
            itemViews.add(new CompareItemView(
                    row.productId(),
                    row.name(),
                    row.quantity().stripTrailingZeros().toPlainString(),
                    priceViews,
                    comparison.common().contains(row.productId()),
                    comparison.unitMismatch().contains(row.productId())));
        }
        List<CompareMarketView> marketViews = chosen.stream()
                .map(market -> new CompareMarketView(
                        market.text(),
                        market.name(),
                        comparison.totals().get(market.id()).toPlainString(),
                        comparison.missing().get(market.id())))
                .toList();
        return new CompareView(
                suggested(rows, prices),
                marketViews,
                comparison.commonCount(),
                comparison.unitMismatch().size(),
                itemViews);
    }

    private List<Chosen> choose(UUID workspaceId, @Nullable String marketsParam) {
        if (marketsParam == null || marketsParam.isBlank()) {
            return List.of();
        }
        String[] parts = marketsParam.split(",", -1);
        if (parts.length < MIN_MARKETS || parts.length > MAX_MARKETS) {
            throw CompareException.marketsInvalid();
        }
        // Every check that needs no lookup comes first, so a bad request is a 422 whatever the refs name.
        List<Optional<MarketRef>> refs = new ArrayList<>();
        Set<Object> seen = new HashSet<>();
        for (String part : parts) {
            String text = part.strip();
            Optional<MarketRef> ref = MarketRef.parse(text);
            refs.add(ref);
            if (!seen.add(ref.isPresent() ? ref.get() : text)) {
                throw CompareException.marketsInvalid();
            }
        }
        List<Chosen> chosen = new ArrayList<>();
        for (Optional<MarketRef> ref : refs) {
            chosen.add(resolve(workspaceId, ref.orElseThrow(MarketNotFoundException::new)));
        }
        // The same market by its family id and by its OSM ref is still one market.
        if (chosen.stream().map(Chosen::id).distinct().count() != chosen.size()) {
            throw CompareException.marketsInvalid();
        }
        return chosen;
    }

    /** An {@code osm-...} market the family never used has no row: it is a market with no prices. */
    private Chosen resolve(UUID workspaceId, MarketRef ref) {
        Optional<MarketRow> row = markets.findByRef(workspaceId, ref);
        return switch (ref) {
            case FamilyRef family -> {
                MarketRow found = row.orElseThrow(MarketNotFoundException::new);
                yield new Chosen(family.id().toString(), found.id(), found.name());
            }
            case OsmRef osmRef -> {
                String text = osmRef.asText();
                String name = row.map(MarketRow::name)
                        .or(() -> osm.find(osmRef).map(OsmObject::name))
                        .orElse(null);
                UUID id = row.map(MarketRow::id)
                        .orElseGet(() -> UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8)));
                yield new Chosen(text, id, name);
            }
        };
    }

    /** The last price of each product at each market, by product. */
    private Map<UUID, List<LatestPrice>> pricesByProduct(UUID workspaceId) {
        Map<UUID, List<LatestPrice>> byProduct = new HashMap<>();
        for (LatestPrice price : prices.latestPerProductAndMarket(workspaceId)) {
            byProduct
                    .computeIfAbsent(price.productId(), id -> new ArrayList<>())
                    .add(price);
        }
        return byProduct;
    }

    /** Prices are told apart by the market's row id, not by the text it was asked for: an OSM market has two. */
    private static Map<UUID, PricePoint> pointsOf(@Nullable List<LatestPrice> prices, List<Chosen> chosen) {
        Map<UUID, PricePoint> byMarket = new HashMap<>();
        if (prices == null) {
            return byMarket;
        }
        for (Chosen market : chosen) {
            for (LatestPrice price : prices) {
                if (price.marketId().equals(market.id())) {
                    byMarket.put(
                            market.id(),
                            new PricePoint(price.unitPrice(), ProductUnit.valueOf(price.unit()), price.observedOn()));
                }
            }
        }
        return byMarket;
    }

    private static ComparePriceView priceView(String ref, PricePoint point, LocalDate oldBefore) {
        return new ComparePriceView(
                ref,
                point.unitPrice().toPlainString(),
                point.unit(),
                point.observedOn(),
                point.observedOn().isBefore(oldBefore));
    }

    /** The two markets with the most list items priced; a tie goes to the one with the newest price. */
    private static List<String> suggested(List<ItemRow> rows, Map<UUID, List<LatestPrice>> prices) {
        Map<String, Integer> count = new HashMap<>();
        Map<String, LocalDate> newest = new HashMap<>();
        for (ItemRow row : rows) {
            for (LatestPrice price : prices.getOrDefault(row.productId(), List.of())) {
                count.merge(price.marketRef(), 1, Integer::sum);
                newest.merge(price.marketRef(), price.observedOn(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        if (count.size() < MIN_MARKETS) {
            return List.of();
        }
        return count.keySet().stream()
                .sorted(Comparator.<String, Integer>comparing(count::get)
                        .reversed()
                        .thenComparing(newest::get, Comparator.reverseOrder())
                        .thenComparing(Comparator.naturalOrder()))
                .limit(MIN_MARKETS)
                .toList();
    }
}
