package br.com.liberdadefamilia.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.indicators.internal.IndicatorCode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** What V18 seeds: one row per indicator code, with the units, and nothing collected yet. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IndicatorSeedIT {

    @Autowired
    JdbcClient jdbc;

    @Test
    void seedMatchesTheEnum() {
        List<Map<String, Object>> rows = jdbc.sql(
                        "select code, source, source_ref, unit, name, last_success_at, last_failure_at,"
                                + " failure_streak from indicator_series")
                .query()
                .listOfRows();

        assertThat(rows)
                .extracting(r -> (String) r.get("code"))
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(IndicatorCode.values()).map(Enum::name).toList());
        assertThat(rows)
                .extracting(r -> r.get("code") + "|" + r.get("source") + "|" + r.get("source_ref") + "|" + r.get("unit")
                        + "|" + r.get("name"))
                .containsExactlyInAnyOrder(
                        "SELIC_TARGET|BCB_SGS|432|PERCENT_YEAR|Selic (meta)",
                        "CDI|BCB_SGS|4389|PERCENT_YEAR|CDI",
                        "IPCA_MONTH|BCB_SGS|433|PERCENT_MONTH|IPCA do mês",
                        "IPCA_12M|BCB_SGS|13522|PERCENT_12M|IPCA em 12 meses",
                        "USD_PTAX_SELL|BCB_PTAX|CotacaoDolarPeriodo|BRL|Dólar (PTAX venda)");
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.get("last_success_at")).isNull();
            assertThat(r.get("last_failure_at")).isNull();
            assertThat(((Number) r.get("failure_streak")).intValue()).isZero();
        });
    }
}
