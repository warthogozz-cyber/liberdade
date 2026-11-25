package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The address search switched off: it needs its own application context, since the switch is a property. */
@SpringBootTest(properties = "liberdade.markets.geocoding.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GeocodingOffIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @MockitoSpyBean
    Plans plans;

    private UUID ana;
    private UUID ws;

    @BeforeEach
    void family() {
        doReturn(false).when(plans).legalSetEnabled();
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'markets', true)")
                .param("ws", ws)
                .update();
    }

    @Test
    void offIs503AndSettingsSaySo() {
        var session = TestUsers.signedIn(ana, null);
        MvcTestResult result = mvc.method(HttpMethod.POST)
                .uri("/v1/workspaces/" + ws + "/markets/geocode")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"Rua Exemplo 10\"}")
                .exchange();

        assertThat(result).hasStatus(503);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("markets.geocoding_off");

        MvcTestResult settings = mvc.method(HttpMethod.GET)
                .uri("/v1/workspaces/" + ws + "/markets/settings")
                .with(session)
                .exchange();
        assertThat(settings).bodyJson().extractingPath("$.geocodingEnabled").isEqualTo(false);
    }
}
