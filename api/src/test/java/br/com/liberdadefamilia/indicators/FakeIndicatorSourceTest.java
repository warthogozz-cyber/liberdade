package br.com.liberdadefamilia.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.indicators.internal.FakeIndicatorSource;
import br.com.liberdadefamilia.indicators.internal.FetchedPoint;
import br.com.liberdadefamilia.indicators.internal.IndicatorCode;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FakeIndicatorSourceTest {

    private final FakeIndicatorSource source = new FakeIndicatorSource();
    private final LocalDate to = LocalDate.of(2026, 11, 20);

    @Test
    void selicChangesTwentyDaysBack() {
        List<FetchedPoint> points = source.fetch(IndicatorCode.SELIC_TARGET, "432", to.minusDays(60), to);

        assertThat(points).isNotEmpty();
        assertThat(points)
                .allSatisfy(p -> assertThat(p.sourceUrl()).isEqualTo("fake:SELIC_TARGET"))
                .allSatisfy(p -> assertThat(p.refDate().getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
                .allSatisfy(p -> assertThat(p.refDate()).isBetween(to.minusDays(60), to));
        assertThat(points).extracting(FetchedPoint::refDate).isSorted();
        assertThat(points.getFirst().value()).isEqualByComparingTo(new BigDecimal("14.75"));
        assertThat(points.getLast().value()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(points)
                .filteredOn(p -> p.refDate().isBefore(to.minusDays(20)))
                .allSatisfy(p -> assertThat(p.value()).isEqualByComparingTo(new BigDecimal("14.75")));
        assertThat(points)
                .filteredOn(p -> !p.refDate().isBefore(to.minusDays(20)))
                .allSatisfy(p -> assertThat(p.value()).isEqualByComparingTo(new BigDecimal("15.00")));
    }

    @Test
    void monthlyOnTheFirst() {
        List<FetchedPoint> points = source.fetch(IndicatorCode.IPCA_12M, "13522", LocalDate.of(2026, 5, 1), to);

        assertThat(points).extracting(FetchedPoint::refDate).allMatch(d -> d.getDayOfMonth() == 1);
        assertThat(points).hasSize(7);
        assertThat(points.getFirst().value()).isEqualByComparingTo(new BigDecimal("4.10"));
        assertThat(points.getLast().refDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(points.getLast().value()).isEqualByComparingTo(new BigDecimal("4.00"));
    }

    @Test
    void ipcaMonthEndsOnItsLastValue() {
        List<FetchedPoint> points = source.fetch(IndicatorCode.IPCA_MONTH, "433", LocalDate.of(2026, 5, 1), to);

        assertThat(points.getFirst().value()).isEqualByComparingTo(new BigDecimal("0.40"));
        assertThat(points.getLast().value()).isEqualByComparingTo(new BigDecimal("0.50"));
    }

    @Test
    void cdiIsFlatAndDollarEndsLower() {
        assertThat(source.fetch(IndicatorCode.CDI, "4389", to.minusDays(10), to))
                .extracting(FetchedPoint::value)
                .allSatisfy(v -> assertThat(v).isEqualByComparingTo(new BigDecimal("14.90")));

        List<FetchedPoint> dollar =
                source.fetch(IndicatorCode.USD_PTAX_SELL, "CotacaoDolarPeriodo", to.minusDays(10), to);
        assertThat(dollar.getFirst().value()).isEqualByComparingTo(new BigDecimal("5.4321"));
        assertThat(dollar.getLast().value()).isEqualByComparingTo(new BigDecimal("5.4100"));
        assertThat(dollar.getLast().refDate()).isEqualTo(to);
    }

    @Test
    void emptyWhenTheWindowHoldsNoPublishedDay() {
        // 21 and 22 November 2026 are a weekend.
        assertThat(source.fetch(IndicatorCode.CDI, "4389", LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 22)))
                .isEmpty();
    }
}
