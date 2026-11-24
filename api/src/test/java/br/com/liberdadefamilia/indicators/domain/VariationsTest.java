package br.com.liberdadefamilia.indicators.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariationsTest {

    private static Point p(String date, String value) {
        return new Point(LocalDate.parse(date), new BigDecimal(value));
    }

    @Test
    void lastChangeFindsTheNewRate() {
        List<Point> points = List.of(
                p("2026-11-01", "14.75"),
                p("2026-11-02", "14.75"),
                p("2026-11-03", "14.75"),
                p("2026-11-05", "15.25"),
                p("2026-11-06", "15.25"));

        Variation v = Variations.lastChange(points);

        assertThat(v).isNotNull();
        assertThat(v.value()).isEqualByComparingTo("0.50");
        assertThat(v.value().scale()).isEqualTo(2);
        assertThat(v.unit()).isEqualTo(VariationUnit.PP);
        assertThat(v.basis()).isEqualTo(VariationBasis.LAST_CHANGE);
        assertThat(v.since()).isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void lastChangeNullWithoutChange() {
        List<Point> points = List.of(p("2026-11-01", "15.00"), p("2026-11-02", "15.00"), p("2026-11-03", "15.00"));
        assertThat(Variations.lastChange(points)).isNull();
    }

    @Test
    void vsPreviousInPoints() {
        List<Point> points = List.of(p("2026-11-20", "4.10"), p("2026-11-23", "4.00"));

        Variation v = Variations.vsPrevious(points, VariationUnit.PP);

        assertThat(v).isNotNull();
        assertThat(v.value()).isEqualByComparingTo("-0.10");
        assertThat(v.unit()).isEqualTo(VariationUnit.PP);
        assertThat(v.basis()).isEqualTo(VariationBasis.PREVIOUS);
        assertThat(v.since()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void vsPreviousInPercent() {
        List<Point> points = List.of(p("2026-11-20", "5.4321"), p("2026-11-23", "5.4100"));

        Variation v = Variations.vsPrevious(points, VariationUnit.PERCENT);

        assertThat(v).isNotNull();
        assertThat(v.value()).isEqualByComparingTo("-0.41");
        assertThat(v.value().scale()).isEqualTo(2);
        assertThat(v.unit()).isEqualTo(VariationUnit.PERCENT);
        assertThat(v.since()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void singlePointHasNoVariation() {
        List<Point> one = List.of(p("2026-11-20", "4.10"));
        assertThat(Variations.lastChange(one)).isNull();
        assertThat(Variations.vsPrevious(one, VariationUnit.PP)).isNull();
        assertThat(Variations.vsPrevious(one, VariationUnit.PERCENT)).isNull();
    }
}
