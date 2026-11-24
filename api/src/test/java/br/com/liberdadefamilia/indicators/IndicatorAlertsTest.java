package br.com.liberdadefamilia.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.indicators.internal.IndicatorAlerts;
import br.com.liberdadefamilia.indicators.internal.IndicatorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class IndicatorAlertsTest {

    @Test
    void formats(CapturedOutput output) {
        IndicatorAlerts alerts = new IndicatorAlerts();

        alerts.fetchFailed(IndicatorCode.CDI);
        alerts.stale(IndicatorCode.IPCA_12M);
        alerts.run(4, 1);

        assertThat(output.getAll())
                .contains("WARN")
                .contains("INDICATOR_ALERT type=FETCH_FAILED series=CDI")
                .contains("INDICATOR_ALERT type=STALE series=IPCA_12M")
                .contains("INFO")
                .contains("INDICATOR_RUN ok=4 failed=1");
        // Past the log prefix, a line is the type and the code (or the two counts), nothing else.
        assertThat(output.getAll().lines().filter(l -> l.contains("INDICATOR_")).toList())
                .allSatisfy(
                        line -> assertThat(line.substring(line.indexOf("INDICATOR_")))
                                .matches(
                                        "INDICATOR_ALERT type=[A-Z_]+ series=[A-Z_0-9]+|INDICATOR_RUN ok=[0-9]+ failed=[0-9]+"));
        assertThat(output.getAll()
                        .lines()
                        .filter(l -> l.contains("INDICATOR_ALERT"))
                        .toList())
                .allSatisfy(line -> assertThat(line).contains("WARN"));
        assertThat(output.getAll()
                        .lines()
                        .filter(l -> l.contains("INDICATOR_RUN"))
                        .toList())
                .allSatisfy(line -> assertThat(line).contains("INFO"));
    }
}
