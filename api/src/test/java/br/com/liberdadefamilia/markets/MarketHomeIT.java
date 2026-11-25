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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The family's address on the Feira: one point with 3 decimals and a name the person chose. */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
class MarketHomeIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    ApplicationEvents events;

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
        events.clear();
    }

    @Test
    void setReadClear() {
        MvcTestResult put = put("{\"lat\":\"-23.123\",\"lon\":\"-46.456\",\"label\":\"Casa\"}");
        assertThat(put).hasStatus(200);
        assertThat(put).bodyJson().extractingPath("$.lat").isEqualTo("-23.123");

        MvcTestResult read = get();
        assertThat(read).hasStatus(200);
        assertThat(read).bodyJson().extractingPath("$.lat").isEqualTo("-23.123");
        assertThat(read).bodyJson().extractingPath("$.lon").isEqualTo("-46.456");
        assertThat(read).bodyJson().extractingPath("$.label").isEqualTo("Casa");
        assertThat(events.stream(MarketHomeSet.class)).containsExactly(new MarketHomeSet(ws, ana));

        assertThat(delete()).hasStatus(204);

        assertThat(get()).hasStatus(204);
        assertThat(events.stream(MarketHomeCleared.class)).containsExactly(new MarketHomeCleared(ws, ana));
        assertThat(count()).isZero();
    }

    @Test
    void noAddressYetIs204() {
        assertThat(get()).hasStatus(204);
    }

    @Test
    void fourDecimalsIs422() {
        MvcTestResult result = put("{\"lat\":\"-10.0001\",\"lon\":\"-50.000\"}");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("markets.location_too_precise");
        assertThat(count()).isZero();
        assertThat(events.stream(MarketHomeSet.class)).isEmpty();
    }

    @Test
    void trailingZerosAreNotDecimals() {
        assertThat(put("{\"lat\":\"-10.1000\",\"lon\":\"-50\"}")).hasStatus(200);

        assertThat(get()).bodyJson().extractingPath("$.lat").isEqualTo("-10.100");
        assertThat(get()).bodyJson().extractingPath("$.lon").isEqualTo("-50.000");
    }

    @Test
    void aPointOffTheGlobeIs422() {
        MvcTestResult result = put("{\"lat\":\"95.000\",\"lon\":\"-50.000\"}");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("markets.location_invalid");
    }

    @Test
    void aLabelOver40CharactersIs422() {
        assertThat(put("{\"lat\":\"-10.000\",\"lon\":\"-50.000\",\"label\":\"" + "a".repeat(41) + "\"}"))
                .hasStatus(422);
        assertThat(put("{\"lat\":\"-10.000\",\"lon\":\"-50.000\",\"label\":\"" + "a".repeat(40) + "\"}"))
                .hasStatus(200);
    }

    @Test
    void aBlankLabelIsNoLabel() {
        assertThat(put("{\"lat\":\"-10.000\",\"lon\":\"-50.000\",\"label\":\"   \"}"))
                .hasStatus(200);

        assertThat(get()).bodyJson().extractingPath("$.label").isNull();
    }

    @Test
    void settingAgainReplacesTheAddress() {
        put("{\"lat\":\"-23.123\",\"lon\":\"-46.456\",\"label\":\"Casa\"}");
        put("{\"lat\":\"-23.200\",\"lon\":\"-46.500\"}");

        assertThat(count()).isEqualTo(1);
        assertThat(get()).bodyJson().extractingPath("$.lat").isEqualTo("-23.200");
        assertThat(get()).bodyJson().extractingPath("$.label").isNull();
        assertThat(events.stream(MarketHomeSet.class)).hasSize(2);
    }

    @Test
    void clearingNothingPublishesNothing() {
        assertThat(delete()).hasStatus(204);

        assertThat(events.stream(MarketHomeCleared.class)).isEmpty();
    }

    private long count() {
        return jdbc.sql("select count(*) from market_home where workspace_id = :ws")
                .param("ws", ws)
                .query(Long.class)
                .single();
    }

    private MvcTestResult get() {
        return mvc.method(HttpMethod.GET)
                .uri("/v1/workspaces/" + ws + "/markets/home")
                .with(TestUsers.signedIn(ana, null))
                .exchange();
    }

    private MvcTestResult put(String body) {
        var session = TestUsers.signedIn(ana, null);
        return mvc.method(HttpMethod.PUT)
                .uri("/v1/workspaces/" + ws + "/markets/home")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult delete() {
        var session = TestUsers.signedIn(ana, null);
        return mvc.method(HttpMethod.DELETE)
                .uri("/v1/workspaces/" + ws + "/markets/home")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();
    }
}
