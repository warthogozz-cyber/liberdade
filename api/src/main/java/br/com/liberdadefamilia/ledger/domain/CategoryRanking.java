package br.com.liberdadefamilia.ledger.domain;

import br.com.liberdadefamilia.shared.Money;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Puts the biggest spending categories first and gathers the rest in one last line. */
public final class CategoryRanking {

    private static final Comparator<CategoryTotal> ORDER = Comparator.comparing(CategoryTotal::current)
            .reversed()
            .thenComparing(Comparator.comparing(CategoryTotal::previous).reversed())
            .thenComparing(CategoryTotal::categoryId);

    private CategoryRanking() {}

    /**
     * Drops the lines where both months are zero, orders the others by this month, then last month, then id, keeps the
     * first {@code limit} and adds a last line without a category for the sum of the rest, if any is left.
     */
    public static List<RankedCategory> rank(List<CategoryTotal> totals, int limit) {
        List<CategoryTotal> sorted = totals.stream()
                .filter(total -> total.current().amount().signum() != 0
                        || total.previous().amount().signum() != 0)
                .sorted(ORDER)
                .toList();
        List<RankedCategory> ranked = new ArrayList<>();
        for (CategoryTotal total : sorted.subList(0, Math.min(limit, sorted.size()))) {
            ranked.add(new RankedCategory(total.categoryId(), total.current(), total.previous()));
        }
        if (sorted.size() > limit) {
            List<CategoryTotal> rest = sorted.subList(limit, sorted.size());
            Money current = rest.get(0).current();
            Money previous = rest.get(0).previous();
            for (CategoryTotal total : rest.subList(1, rest.size())) {
                current = current.plus(total.current());
                previous = previous.plus(total.previous());
            }
            ranked.add(new RankedCategory(null, current, previous));
        }
        return List.copyOf(ranked);
    }
}
