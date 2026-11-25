package br.com.liberdadefamilia.ventures.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.ventures.domain.CostLine;
import br.com.liberdadefamilia.ventures.domain.PaybackState;
import br.com.liberdadefamilia.ventures.domain.ProductLine;
import br.com.liberdadefamilia.ventures.domain.VentureCalculator;
import br.com.liberdadefamilia.ventures.domain.VentureFigures;
import br.com.liberdadefamilia.ventures.domain.VentureType;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TemplateCatalogTest {

    private static final List<String> KEYS =
            List.of("food-sweets", "digital-ebook", "content-channel", "resale-goods", "service-hours");

    private static List<Template> all() {
        return KEYS.stream().map(k -> TemplateCatalog.template(k).orElseThrow()).toList();
    }

    private static VentureFigures figures(Template t) {
        return VentureCalculator.calculate(
                t.initial().stream().map(i -> new CostLine(i.amount())).toList(),
                t.fixed().stream().map(i -> new CostLine(i.amount())).toList(),
                t.products().stream()
                        .map(p -> new ProductLine(p.unitVariableCost(), p.unitPrice(), p.plannedUnitsMonth()))
                        .toList());
    }

    private static BigDecimal sum(List<TemplateItem> items) {
        return items.stream().map(TemplateItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void tracksInOrder() {
        List<Track> tracks = TemplateCatalog.tracks();

        assertThat(tracks.stream().map(Track::key))
                .containsExactly("food", "digital", "content", "resale-services", "investments");
        assertThat(tracks.stream().filter(Track::educational).map(Track::key)).containsExactly("investments");
        assertThat(tracks.get(4).templates()).isEmpty();
    }

    @Test
    void fiveTemplates() {
        assertThat(all().stream().map(Template::type))
                .containsExactly(
                        VentureType.FOOD,
                        VentureType.DIGITAL_PRODUCT,
                        VentureType.CONTENT,
                        VentureType.RESALE,
                        VentureType.SERVICE);
        assertThat(TemplateCatalog.template("nope")).isEmpty();
    }

    @Test
    void foodSweetsIsThePrdExample() {
        Template t = TemplateCatalog.template("food-sweets").orElseThrow();

        assertThat(sum(t.initial())).isEqualByComparingTo("800.00");
        assertThat(sum(t.fixed())).isEqualByComparingTo("270.00");
        assertThat(t.products()).hasSize(1);
        TemplateProduct p = t.products().get(0);
        assertThat(p.unitVariableCost()).isEqualByComparingTo("1.80");
        assertThat(p.unitPrice()).isEqualByComparingTo("4.50");
        assertThat(p.plannedUnitsMonth()).isEqualTo(300);
        VentureFigures f = figures(t);
        assertThat(f.monthlyProfit()).isEqualByComparingTo("540.00");
        assertThat(f.paybackMonths()).isEqualTo(2L);
        assertThat(f.roi12mPercent()).isEqualByComparingTo("710.0");
    }

    @Test
    void everyTemplatePaysBackWithinAYear() {
        for (Template t : all()) {
            VentureFigures f = figures(t);
            assertThat(f.monthlyProfit()).as(t.key()).isPositive();
            assertThat(f.paybackState()).as(t.key()).isEqualTo(PaybackState.MONTHS);
            assertThat(f.paybackMonths()).as(t.key()).isLessThanOrEqualTo(12L);
        }
    }

    @Test
    void everyTemplateHasFiveToSevenSteps() {
        for (Template t : all()) {
            assertThat(t.steps()).as(t.key()).hasSizeBetween(5, 7);
            assertThat(t.steps()).allSatisfy(s -> assertThat(s.length()).isBetween(1, 80));
        }
    }

    @Test
    void templatesRespectTheLimits() {
        for (Template t : all()) {
            assertThat(t.initial().size()).isLessThanOrEqualTo(20);
            assertThat(t.fixed().size()).isLessThanOrEqualTo(20);
            assertThat(t.products().size()).isLessThanOrEqualTo(10);
            assertThat(t.name().length()).isBetween(1, 40);
            t.initial().forEach(i -> checkItem(i.name(), i.amount()));
            t.fixed().forEach(i -> checkItem(i.name(), i.amount()));
            t.products().forEach(p -> {
                checkItem(p.name(), p.unitVariableCost());
                checkItem(p.name(), p.unitPrice());
                assertThat(p.plannedUnitsMonth()).isPositive();
            });
        }
    }

    private static void checkItem(String name, BigDecimal amount) {
        assertThat(name.length()).isBetween(1, 40);
        assertThat(amount).isPositive();
        assertThat(amount.scale()).isLessThanOrEqualTo(2);
    }

    @Test
    void stepsMakeNoLegalClaims() {
        Pattern forbidden = Pattern.compile("R\\$|%|obrigat|prazo|\\d", Pattern.CASE_INSENSITIVE);
        for (Template t : all()) {
            assertThat(t.steps())
                    .allSatisfy(s -> assertThat(forbidden.matcher(s).find()).isFalse());
        }
    }

    @Test
    void blankHasThreeSteps() {
        assertThat(TemplateCatalog.BLANK_STEPS)
                .containsExactly(
                        "Anote os custos do começo e do mês",
                        "Defina o preço de cada produto",
                        "Faça a primeira venda");
    }
}
