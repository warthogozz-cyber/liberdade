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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

/**
 * The plan guard on the Feira's routes, as on the module's others: billing on and the Grátis plan is a 403
 * {@code plan.required}; with billing off the routes are open. The other routes that write share the module's guards and
 * are covered by the role matrix.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MarketPlanGuardIT {

    private static final String SEARCH = "{\"lat\":\"-23.00\",\"lon\":\"-46.00\"}";
    private static final String LINKS = "{\"website\":\"https://familia.example.com\",\"instagram\":null,"
            + "\"flyerUrl\":null,\"flyerSource\":null,\"flyerValidUntil\":null}";

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
    private UUID market;

    @BeforeEach
    void family() {
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'markets', true)")
                .param("ws", ws)
                .update();
        market = MarketsTestData.family(jdbc, ws, "Feira Exemplo", "-23.00300", "-45.99900");
    }

    @ParameterizedTest
    @ValueSource(strings = {"search", "settings", "card", "links"})
    void billingOnAndFreePlanIs403(String route) {
        doReturn(true).when(plans).legalSetEnabled();

        MvcTestResult result = call(route);

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("plan.required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"search", "settings", "card", "links"})
    void billingOffIsOpen(String route) {
        doReturn(false).when(plans).legalSetEnabled();

        assertThat(call(route)).hasStatus(200);
    }

    private MvcTestResult call(String route) {
        var session = TestUsers.signedIn(ana, null);
        String base = "/v1/workspaces/" + ws + "/markets";
        return switch (route) {
            case "search" ->
                mvc.method(HttpMethod.POST)
                        .uri(base + "/search")
                        .with(session)
                        .with(CsrfSession.start(mvc, session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SEARCH)
                        .exchange();
            case "settings" -> mvc.get().uri(base + "/settings").with(session).exchange();
            case "links" ->
                mvc.method(HttpMethod.PUT)
                        .uri(base + "/" + market + "/links")
                        .with(session)
                        .with(CsrfSession.start(mvc, session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LINKS)
                        .exchange();
            default -> mvc.get().uri(base + "/" + market).with(session).exchange();
        };
    }
}
