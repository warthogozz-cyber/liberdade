package br.com.liberdadefamilia.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class SuggestedContributionTest {

    private static final YearMonth CURRENT = YearMonth.of(2026, 11);
    private static final BigDecimal TARGET = new BigDecimal("12000");
    private static final BigDecimal SAVED = new BigDecimal("3000");

    @Test
    void prdNineMonths() {
        assertThat(SuggestedContribution.of(TARGET, SAVED, CURRENT.plusMonths(9), CURRENT))
                .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("1000.00"));
    }

    @Test
    void prdSevenMonthsRoundsUp() {
        assertThat(SuggestedContribution.of(TARGET, SAVED, CURRENT.plusMonths(7), CURRENT))
                .hasValueSatisfying(v -> assertThat(v).isEqualTo(new BigDecimal("1285.72")));
    }

    @Test
    void pastDueUsesOneMonth() {
        assertThat(SuggestedContribution.of(TARGET, SAVED, CURRENT.minusMonths(1), CURRENT))
                .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("9000.00"));
    }

    @Test
    void noDueOrAchievedIsEmpty() {
        assertThat(SuggestedContribution.of(TARGET, SAVED, null, CURRENT)).isEmpty();
        assertThat(SuggestedContribution.of(TARGET, TARGET, CURRENT.plusMonths(3), CURRENT))
                .isEmpty();
        assertThat(SuggestedContribution.of(TARGET, new BigDecimal("13000"), CURRENT.plusMonths(3), CURRENT))
                .isEmpty();
    }
}
