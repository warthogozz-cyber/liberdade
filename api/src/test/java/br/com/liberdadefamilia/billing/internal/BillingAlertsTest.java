package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class BillingAlertsTest {

    @Test
    void raiseLogsOnlyTheTypeOnTheAlertsLogger(CapturedOutput output) {
        new BillingAlerts().raise(AlertType.CHARGEBACK);

        assertThat(output.getAll())
                .contains("br.com.liberdadefamilia.billing.alerts")
                .contains("BILLING_ALERT type=CHARGEBACK");
    }
}
