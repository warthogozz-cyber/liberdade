package br.com.liberdadefamilia.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestUsers;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The versions of the terms and the policy in force follow the billing switch, and anyone may ask for them. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LegalCurrentIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    void billingOnReturnsDecemberSet() {
        MvcTestResult result = mvc.get().uri("/v1/legal/current").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.terms").isEqualTo("terms-2026-12");
        assertThat(result).bodyJson().extractingPath("$.privacy").isEqualTo("privacy-2026-12.4");
        assertThat(result).headers().hasValue(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    /** With billing off, the texts in force are the ones from before billing. */
    @Nested
    @TestPropertySource(properties = "liberdade.billing.enabled=false")
    class BillingOff {

        @Autowired
        MockMvcTester mvc;

        @Autowired
        TestUsers users;

        @Test
        void billingOffReturnsNovemberSet() {
            MvcTestResult result = mvc.get().uri("/v1/legal/current").exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.terms").isEqualTo("terms-2026-11");
            assertThat(result).bodyJson().extractingPath("$.privacy").isEqualTo("privacy-2026-11.4");
        }

        @Test
        void documentsAskForTheNovemberSet() {
            UUID ana = users.create("Ana Teste", "ana@example.com");

            MvcTestResult result = mvc.get()
                    .uri("/v1/me/documents")
                    .with(TestUsers.signedIn(ana, null))
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$[0].currentVersion").isEqualTo("terms-2026-11");
            assertThat(result).bodyJson().extractingPath("$[1].currentVersion").isEqualTo("privacy-2026-11.4");
        }
    }
}
