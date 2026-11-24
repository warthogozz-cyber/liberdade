package br.com.liberdadefamilia.indicators;

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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The read route: behind the module switch, scoped to the family of the caller. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IndicatorsIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    private MvcTestResult setIndicators(UUID user, UUID ws, boolean enabled) {
        return mvc.put()
                .uri("/v1/workspaces/" + ws + "/modules/indicators")
                .with(TestUsers.signedIn(user, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(user, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":" + enabled + "}")
                .exchange();
    }

    private MvcTestResult get(UUID user, UUID ws) {
        return mvc.get()
                .uri("/v1/workspaces/" + ws + "/indicators")
                .with(TestUsers.signedIn(user, null))
                .exchange();
    }

    @Test
    void moduleOnReturnsCards() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        assertThat(setIndicators(ana, ws, true)).hasStatusOk();

        MvcTestResult result = get(ana, ws);

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.indicators[*].code")
                .asArray()
                .containsExactly("SELIC_TARGET", "CDI", "IPCA_MONTH", "IPCA_12M", "USD_PTAX_SELL");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.indicators[0].source.name")
                .isEqualTo("Banco Central do Brasil");
    }

    @Test
    void moduleOffIs403() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);

        MvcTestResult result = get(ana, ws);

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("module.disabled");
    }

    @Test
    void otherFamilyIs404() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        assertThat(setIndicators(ana, ws, true)).hasStatusOk();
        UUID bia = users.create("Bia Teste", "bia@example.com");

        assertThat(get(bia, ws)).hasStatus(404);
    }
}
