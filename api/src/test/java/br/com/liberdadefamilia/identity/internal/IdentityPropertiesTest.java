package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class IdentityPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IdentityProperties.class)
    static class Properties {}

    /** The relying party is the host of the origin, which is always consistent with it. */
    private static ApplicationContextRunner withOrigin(String origin) {
        return withOriginAndRpId(origin, origin.replaceFirst("^[a-z]+://", "").replaceFirst("[:/].*$", ""));
    }

    private static ApplicationContextRunner withOriginAndRpId(String origin, String rpId) {
        return new ApplicationContextRunner()
                .withUserConfiguration(Properties.class)
                .withPropertyValues(
                        "liberdade.web.origin=" + origin,
                        "liberdade.webauthn.rp-id=" + rpId,
                        "liberdade.webauthn.rp-name=Liberdade");
    }

    @Test
    void acceptsProductionOrigin() {
        withOrigin("https://app.liberdadefamilia.com.br")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void acceptsLocalhost() {
        withOrigin("http://localhost:5173").run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "https://app.example.com",
                "https://app.example.com:8443"
            })
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
                "ftp://app.example.com",
                "http://a@b",
                "https://user@app.example.com",
                "http://app.example.com",
                "https://APP.example.com",
                "https://app.example.com?x=1",
                "https://app.example.com#top"
            })
    void rejectsBadOrigins(String origin) {
        withOrigin(origin).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(BindValidationException.class)
                    .hasMessageContaining("liberdade.web.origin");
        });
    }

    @Test
    void rejectsRpIdOutsideOrigin() {
        withOriginAndRpId("https://app.example.com", "other.example.com").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(BindValidationException.class)
                    .hasMessageContaining("rpIdWithinOrigin");
        });
        withOriginAndRpId("https://app.example.com", "example.com")
                .run(context -> assertThat(context).hasNotFailed());
        withOriginAndRpId("https://app.example.com", "pp.example.com")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"rp-id", "rp-name"})
    void rejectsBlankRelyingParty(String property) {
        withOrigin("http://localhost:5173")
                .withPropertyValues("liberdade.webauthn." + property + "= ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(BindValidationException.class)
                            .hasMessageContaining("liberdade.webauthn");
                });
    }

    @Test
    void rejectsMissingWebBlock() {
        new ApplicationContextRunner()
                .withUserConfiguration(Properties.class)
                .withPropertyValues("liberdade.webauthn.rp-id=localhost", "liberdade.webauthn.rp-name=Liberdade")
                .run(context -> assertMissing(context, "web"));
    }

    @Test
    void rejectsMissingWebAuthnBlock() {
        new ApplicationContextRunner()
                .withUserConfiguration(Properties.class)
                .withPropertyValues("liberdade.web.origin=http://localhost:5173")
                .run(context -> assertMissing(context, "webauthn"));
    }

    private static void assertMissing(AssertableApplicationContext context, String block) {
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure())
                .rootCause()
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("field '" + block + "'");
    }
}
