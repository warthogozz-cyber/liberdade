package br.com.liberdadefamilia.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    private static Flow flow(int y, int m, int d, String amount) {
        return new Flow(LocalDate.of(y, m, d), new BigDecimal(amount));
    }

    private static List<Flow> specFlows() {
        return List.of(
                flow(2026, 11, 25, "-2000"),
                flow(2026, 12, 5, "6000"),
                flow(2026, 12, 25, "-2000"),
                flow(2027, 1, 5, "6000"),
                flow(2027, 1, 25, "-2000"),
                flow(2027, 2, 5, "6000"),
                flow(2027, 2, 25, "-2000"));
    }

    private static List<String> balances(List<ProjectionPoint> points) {
        return points.stream().map(p -> p.balance().toPlainString()).toList();
    }

    @Test
    void specExampleWithHistory() {
        List<ProjectionPoint> points =
                Projection.compute(TODAY, new BigDecimal("5000"), specFlows(), new BigDecimal("9900"));

        assertThat(points)
                .extracting(ProjectionPoint::monthEnd)
                .containsExactly(
                        LocalDate.of(2026, 11, 30),
                        LocalDate.of(2026, 12, 31),
                        LocalDate.of(2027, 1, 31),
                        LocalDate.of(2027, 2, 28));
        assertThat(balances(points)).containsExactly("1900.00", "2600.00", "3300.00", "4000.00");
        assertThat(points.get(0).estimatedSpending()).isEqualTo(new BigDecimal("1100.00"));
        assertThat(points.get(0).scheduled()).isEqualTo(new BigDecimal("-2000.00"));
    }

    @Test
    void specExampleWithoutHistory() {
        List<ProjectionPoint> points = Projection.compute(TODAY, new BigDecimal("5000"), specFlows(), null);

        assertThat(balances(points)).containsExactly("3000.00", "7000.00", "11000.00", "15000.00");
    }

    @Test
    void projectionWithoutHistory() {
        List<ProjectionPoint> points = Projection.compute(TODAY, new BigDecimal("5000"), List.of(), null);

        assertThat(points).hasSize(4);
        assertThat(balances(points)).containsOnly("5000.00");
    }

    @Test
    void overdueFlowCountsInFirstPoint() {
        List<ProjectionPoint> points =
                Projection.compute(TODAY, new BigDecimal("5000"), List.of(flow(2026, 11, 10, "-100")), null);

        assertThat(points.get(0).scheduled()).isEqualByComparingTo("-100");
        assertThat(points.get(0).balance()).isEqualByComparingTo("4900");
    }

    @Test
    void lastDayOfMonthHasNoSpending() {
        List<ProjectionPoint> points = Projection.compute(
                LocalDate.of(2026, 11, 30), new BigDecimal("5000"), List.of(), new BigDecimal("9900"));

        assertThat(points.get(0).estimatedSpending()).isEqualByComparingTo("0");
        assertThat(points.get(1).estimatedSpending()).isEqualByComparingTo("3300");
    }
}
