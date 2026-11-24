package br.com.liberdadefamilia.indicators;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.indicators.internal.IndicatorProperties;
import br.com.liberdadefamilia.indicators.internal.IndicatorStartupCheck;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class IndicatorStartupCheckTest {

    private static IndicatorStartupCheck check(String source, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        IndicatorProperties properties = new IndicatorProperties(source, null, null, null, null, null, null);
        return new IndicatorStartupCheck(properties, environment);
    }

    @Test
    void fakeRefusedUnderProd() {
        assertThatThrownBy(() -> check("fake", "prod").run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("indicators.source=fake is not allowed in prod");
    }

    @Test
    void bcbAllowedUnderProd() {
        assertThatCode(() -> check("bcb", "prod").run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void fakeAllowedOutsideProd() {
        assertThatCode(() -> check("fake", "dev").run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }
}
