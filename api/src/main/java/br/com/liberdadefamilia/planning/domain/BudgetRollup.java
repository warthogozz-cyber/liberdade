package br.com.liberdadefamilia.planning.domain;

import br.com.liberdadefamilia.ledger.CategorySpending;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A category spending counts its own rows plus those of its subcategories. */
public final class BudgetRollup {

    private BudgetRollup() {}

    /** The category's own spending plus its children's, at scale 2. */
    public static BigDecimal spentFor(UUID categoryId, List<CategorySpending> rows) {
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (CategorySpending row : rows) {
            if (categoryId.equals(row.categoryId()) || categoryId.equals(row.parentId())) {
                total = total.add(row.amount());
            }
        }
        return total;
    }
}
