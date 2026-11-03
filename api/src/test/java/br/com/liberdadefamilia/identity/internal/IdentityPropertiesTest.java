package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class IdentityPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IdentityProperties.class)
    static class Properties {}

    private static ApplicationContextRunner withOrigin(String origin) {
        return new ApplicationContextRunner()
                .withUserConfiguration(Properties.class)
                .withPropertyValues(
                        "liberdade.web.origin=" + origin,
                        "liberdade.webauthn.rp-id=localhost",
                        "liberdade.webauthn.rp-name=Liberdade");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173", "https://app.example.com", "https://app.example.com:8443"})
    void acceptsSchemeHostAndPort(String origin) {
        withOrigin(origin).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(IdentityProperties.class).web().origin()).isEqualTo(origin);
        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "   ",
                "http://localhost:5173/",
                "https://app.example.com/",
                "https://app.example.com/app",
                "app.example.com",
                "ftp://app.example.com"
            })
    void rejectsBlankTrailingSlashOrPath(String origin) {
        withOrigin(origin).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(BindValidationException.class)
                    .hasMessageContaining("liberdade.web.origin");
        });
    }
}
