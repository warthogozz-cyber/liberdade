package br.com.liberdadefamilia.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Property tests with a fixed seed (see ADR-0013): 1000 cases per property. */
class ProjectionProperties {

    private static final long SEED = 20261102L;
    private static final int CASES = 1000;

    private static BigDecimal money(SplittableRandom random, int boundCents) {
        return BigDecimal.valueOf(random.nextInt(-boundCents, boundCents + 1), 2);
    }

    @Test
    void balanceIsSumOfParts() {
        SplittableRandom random = new SplittableRandom(SEED);
        for (int i = 0; i < CASES; i++) {
            LocalDate today = LocalDate.of(2026, 1, 1).plusDays(random.nextInt(0, 1500));
            BigDecimal balanceToday = money(random, 100_000_000);
            List<Flow> flows = new ArrayList<>();
            for (int f = random.nextInt(0, 12); f > 0; f--) {
                flows.add(new Flow(today.plusDays(random.nextInt(-40, 160)), money(random, 5_000_000)));
            }
            BigDecimal sum = random.nextInt(0, 4) == 0 ? null : BigDecimal.valueOf(random.nextInt(0, 100_000_000), 2);

            List<ProjectionPoint> points = Projection.compute(today, balanceToday, flows, sum);

            assertThat(points).hasSize(4);
            BigDecimal previousSpending = BigDecimal.ZERO;
            for (ProjectionPoint point : points) {
                assertThat(point.balance())
                        .isEqualByComparingTo(
                                balanceToday.add(point.scheduled()).subtract(point.estimatedSpending()));
                assertThat(point.estimatedSpending()).isGreaterThanOrEqualTo(previousSpending);
                previousSpending = point.estimatedSpending();
            }
        }
    }
}
