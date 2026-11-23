package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(OutputCaptureExtension.class)
class BillingStartupCheckTest {

    private static BillingProperties properties(boolean enabled, String gateway) {
        return new BillingProperties(
                enabled, true, gateway, List.of(), "", new BillingProperties.Asaas("https://x.test", ""));
    }

    private static BillingStartupCheck check(BillingProperties properties, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new BillingStartupCheck(properties, new BillingStatus(properties), environment);
    }

    @Test
    void prodWithFakeFails() {
        assertThatThrownBy(() -> check(properties(true, "fake"), "prod").run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("billing.gateway=fake is not allowed in prod");
    }

    @Test
    void prodWithFakeInAnyCaseFails() {
        assertThatThrownBy(() -> check(properties(true, " FAKE "), "prod").run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("billing.gateway=fake is not allowed in prod");
    }

    @Test
    void fakeOutsideProdIsFine() {
        assertThatCode(() -> check(properties(true, "fake"), "dev").run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledWithoutConfigurationWarnsAndStarts(CapturedOutput output) {
        assertThatCode(() -> check(properties(true, "asaas"), "prod").run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();

        assertThat(output).contains("WARN").contains("billing enabled but not configured");
    }

    @Test
    void disabledIsQuiet(CapturedOutput output) {
        check(properties(false, "asaas"), "prod").run(new DefaultApplicationArguments());

        assertThat(output).doesNotContain("billing enabled but not configured");
    }
}
