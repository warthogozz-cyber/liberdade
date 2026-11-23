package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** With billing off nothing is sold, and the webhook never authenticates. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "liberdade.billing.enabled=false")
class BillingDisabledIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Test
    void planShowsNothingToBuy() {
        UUID owner = users.create("Dono", null);
        UUID ws = workspaces.createFor(owner);

        var result = mvc.get()
                .uri("/v1/workspaces/" + ws + "/billing/plan")
                .with(TestUsers.signedIn(owner, null))
                .exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.presaleOpen").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.canPurchase").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.key").isEqualTo("FREE");
    }

    @Test
    void checkoutIsUnavailable() {
        UUID owner = users.create("Dono", null);
        UUID ws = workspaces.createFor(owner);
        var session = TestUsers.signedIn(owner, null);

        var result = mvc.post()
                .uri("/v1/workspaces/" + ws + "/billing/checkout")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("billing.unavailable");
    }

    @Test
    void webhookWithTheTestTokenIsRejected() {
        var result = mvc.post()
                .uri("/v1/billing/webhooks/asaas")
                .contentType(MediaType.APPLICATION_JSON)
                .header("asaas-access-token", "test-webhook-token-0123456789abcdef0123")
                .content("{\"id\":\"evt_off\",\"event\":\"PAYMENT_CREATED\"}")
                .exchange();

        assertThat(result).hasStatus(401);
    }
}
