package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** A webhook token that is not configured never lets anyone in, not even an empty header. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "liberdade.billing.webhook-token=")
class WebhookBlankTokenIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    void blankConfiguredTokenAlwaysRejects() {
        var result = mvc.post()
                .uri("/v1/billing/webhooks/asaas")
                .contentType(MediaType.APPLICATION_JSON)
                .header("asaas-access-token", "")
                .content("{\"id\":\"evt_blank\",\"event\":\"PAYMENT_CREATED\"}")
                .exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("billing.webhook-unauthorized");
    }
}
