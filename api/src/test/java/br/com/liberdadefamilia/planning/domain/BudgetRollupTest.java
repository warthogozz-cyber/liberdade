package br.com.liberdadefamilia.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.ledger.CategorySpending;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BudgetRollupTest {

    @Test
    void parentIncludesChildren() {
        UUID parent = UUID.randomUUID();
        UUID childA = UUID.randomUUID();
        UUID childB = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        List<CategorySpending> rows = List.of(
                new CategorySpending(parent, null, new BigDecimal("100.00")),
                new CategorySpending(childA, parent, new BigDecimal("50.00")),
                new CategorySpending(childB, parent, new BigDecimal("25.00")),
                new CategorySpending(other, null, new BigDecimal("999.00")));

        assertThat(BudgetRollup.spentFor(parent, rows)).isEqualByComparingTo("175.00");
        assertThat(BudgetRollup.spentFor(childA, rows)).isEqualByComparingTo("50.00");
        assertThat(BudgetRollup.spentFor(UUID.randomUUID(), rows)).isEqualByComparingTo("0.00");
    }
}
