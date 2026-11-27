package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.NameSimilarity;
import br.com.liberdadefamilia.markets.domain.ProductUnit;
import br.com.liberdadefamilia.markets.internal.MarketViews.SuggestedPair;
import br.com.liberdadefamilia.markets.internal.MarketViews.SuggestedProduct;
import br.com.liberdadefamilia.markets.internal.MarketViews.SuggestionsView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pairs of products that may be the same one (D5), and the pairs the family said are not (D6). */
@Service
class ProductSuggestionsService {

    static final double THRESHOLD = 0.5;
    static final int MAX_PAIRS = 20;

    private final JdbcClient jdbc;

    ProductSuggestionsService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record Candidate(UUID id, String name, String unit, String gtin, Set<String> words) {}

    /** Up to {@value #MAX_PAIRS} pairs with a score of at least 0.5, best first; {@code a} is the smaller id. */
    @Transactional(readOnly = true)
    SuggestionsView suggestions(UUID workspaceId) {
        List<Candidate> candidates = jdbc.sql("""
                        select id, name, unit, gtin from products
                        where workspace_id = :ws order by created_at desc, id desc limit :limit""")
                .param("ws", workspaceId)
                .param("limit", ProductsService.MAX_PRODUCTS)
                .query((rs, n) -> new Candidate(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("unit"),
                        rs.getString("gtin"),
                        NameSimilarity.words(rs.getString("name"))))
                .list();
        Set<String> dismissed = new HashSet<>(
                jdbc.sql("select product_a || '/' || product_b from product_join_dismissals where workspace_id = :ws")
                        .param("ws", workspaceId)
                        .query(String.class)
                        .list());
        List<SuggestedPair> pairs = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                Candidate first = candidates.get(i);
                Candidate second = candidates.get(j);
                if (first.gtin() != null
                        && second.gtin() != null
                        && !first.gtin().equals(second.gtin())) {
                    continue;
                }
                double score = NameSimilarity.score(first.words(), second.words());
                if (score < THRESHOLD) {
                    continue;
                }
                boolean firstIsA = isFirst(first.id(), second.id());
                Candidate a = firstIsA ? first : second;
                Candidate b = firstIsA ? second : first;
                if (!dismissed.contains(a.id() + "/" + b.id())) {
                    pairs.add(new SuggestedPair(view(a), view(b), score));
                }
            }
        }
        // Equal scores are common (word overlap is coarse), so the rest of the order is fixed: names, then ids.
        pairs.sort(Comparator.comparingDouble(SuggestedPair::score)
                .reversed()
                .thenComparing(pair -> pair.a().name())
                .thenComparing(pair -> pair.b().name())
                .thenComparing(pair -> pair.a().id())
                .thenComparing(pair -> pair.b().id()));
        return new SuggestionsView(pairs.stream().limit(MAX_PAIRS).toList());
    }

    /**
     * Records that these two are not the same product; telling it again changes nothing.
     *
     * @throws ProductRuleException when both ids are the same
     * @throws ProductNotFoundException when the family has no such product
     */
    @Transactional
    void dismiss(UUID workspaceId, UUID one, UUID other) {
        if (one.equals(other)) {
            throw ProductRuleException.mergeSame();
        }
        int found = jdbc.sql("select count(*) from products where workspace_id = :ws and id in (:one, :other)")
                .param("ws", workspaceId)
                .param("one", one)
                .param("other", other)
                .query(Integer.class)
                .single();
        if (found != 2) {
            throw new ProductNotFoundException();
        }
        UUID a = isFirst(one, other) ? one : other;
        UUID b = a.equals(one) ? other : one;
        jdbc.sql("""
                        insert into product_join_dismissals (workspace_id, product_a, product_b)
                        values (:ws, :a, :b) on conflict do nothing""").param("ws", workspaceId).param("a", a).param("b", b).update();
    }

    /** The order the database gives uuids (unsigned bytes); {@link UUID#compareTo} compares signed longs. */
    static boolean isFirst(UUID x, UUID y) {
        int byHigh = Long.compareUnsigned(x.getMostSignificantBits(), y.getMostSignificantBits());
        int order =
                byHigh != 0 ? byHigh : Long.compareUnsigned(x.getLeastSignificantBits(), y.getLeastSignificantBits());
        return order < 0;
    }

    private static SuggestedProduct view(Candidate c) {
        return new SuggestedProduct(c.id(), c.name(), ProductUnit.valueOf(c.unit()));
    }
}
