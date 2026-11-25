package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

/** The markets the family adds itself, their position, archiving, and the conversion of one that left OpenStreetMap. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FamilyMarketIT {

    private static final String SEARCH = "{\"lat\":\"-23.00\",\"lon\":\"-46.00\"}";

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
        MarketsTestData.clearExtract(jdbc);
        doReturn(false).when(plans).legalSetEnabled();
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'markets', true)")
                .param("ws", ws)
                .update();
    }

    @AfterEach
    void clean() {
        MarketsTestData.clearExtract(jdbc);
    }

    @Test
    void addWithAndWithoutPosition() {
        MvcTestResult located = send(
                HttpMethod.POST,
                "/markets",
                "{\"name\":\"Feira Exemplo\",\"kind\":\"GREENGROCER\",\"lat\":\"-23.00300\",\"lon\":\"-45.99900\"}");
        MvcTestResult unlocated =
                send(HttpMethod.POST, "/markets", "{\"name\":\"Mercado sem ponto\",\"kind\":\"SUPERMARKET\"}");

        assertThat(located).hasStatus(201);
        assertThat(located).bodyJson().extractingPath("$.origin").isEqualTo("FAMILY");
        assertThat(located).bodyJson().extractingPath("$.lat").isEqualTo("-23.00300");
        assertThat(unlocated).hasStatus(201);
        assertThat(unlocated).bodyJson().extractingPath("$.lat").isNull();

        MvcTestResult search = send(HttpMethod.POST, "/markets/search", SEARCH);
        assertThat(search).bodyJson().extractingPath("$.family.length()").isEqualTo(1);
        assertThat(search).bodyJson().extractingPath("$.family[0].name").isEqualTo("Feira Exemplo");
        assertThat(search).bodyJson().extractingPath("$.unlocated.length()").isEqualTo(1);
        assertThat(search).bodyJson().extractingPath("$.unlocated[0].name").isEqualTo("Mercado sem ponto");
    }

    @Test
    void sixDecimalsIs422() {
        MvcTestResult result = send(
                HttpMethod.POST,
                "/markets",
                "{\"name\":\"Feira Exemplo\",\"kind\":\"GREENGROCER\",\"lat\":\"-23.000001\",\"lon\":\"-45.99900\"}");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("markets.location_too_precise");
    }

    @Test
    void onlyOneCoordinateIs422() {
        MvcTestResult result = send(
                HttpMethod.POST,
                "/markets",
                "{\"name\":\"Feira Exemplo\",\"kind\":\"GREENGROCER\",\"lat\":\"-23.00300\"}");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("markets.position_invalid");
        assertThat(jdbc.sql("select count(*) from markets where workspace_id = :ws")
                        .param("ws", ws)
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void positionMovesAFamilyMarket() {
        UUID own = MarketsTestData.family(jdbc, ws, "Feira Exemplo", null, null);

        MvcTestResult result =
                send(HttpMethod.PUT, "/markets/" + own + "/position", "{\"lat\":\"-23.00400\",\"lon\":\"-45.99800\"}");

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.lat").isEqualTo("-23.00400");
        assertThat(result).bodyJson().extractingPath("$.lon").isEqualTo("-45.99800");
    }

    @Test
    void positionNeedsBothCoordinates() {
        UUID own = MarketsTestData.family(jdbc, ws, "Feira Exemplo", null, null);

        MvcTestResult result = send(HttpMethod.PUT, "/markets/" + own + "/position", "{\"lat\":\"-23.00400\"}");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("markets.position_invalid");
    }

    @Test
    void positionOnlyOnFamily() {
        MarketsTestData.osm(jdbc, 1, "Mercado Exemplo 1", "-23.00100", "-46.00100");
        UUID row = MarketsTestData.familyRowForOsm(jdbc, ws, 777);

        for (String ref : new String[] {"osm-n-1", row.toString()}) {
            MvcTestResult result = send(
                    HttpMethod.PUT, "/markets/" + ref + "/position", "{\"lat\":\"-23.00400\",\"lon\":\"-45.99800\"}");

            assertThat(result).as(ref).hasStatus(409);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("market.not_family");
        }
    }

    @Test
    void convertLeftOsm() {
        UUID row = MarketsTestData.familyRowForOsm(jdbc, ws, 777);
        jdbc.sql("update markets set website = 'https://familia.example.com' where id = :id")
                .param("id", row)
                .update();

        MvcTestResult result = send(
                HttpMethod.POST,
                "/markets/" + row + "/convert",
                "{\"name\":\"Mercado Exemplo\",\"kind\":\"SUPERMARKET\",\"lat\":\"-23.00300\",\"lon\":\"-45.99900\"}");

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.origin").isEqualTo("FAMILY");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(result).bodyJson().extractingPath("$.ref").isEqualTo(row.toString());
        assertThat(result).bodyJson().extractingPath("$.cnpj").isEqualTo("12345678000195");
        assertThat(result).bodyJson().extractingPath("$.website.value").isEqualTo("https://familia.example.com");
        var stored = jdbc.sql("select origin, osm_type, osm_id, cnpj, website from markets where id = :id")
                .param("id", row)
                .query()
                .singleRow();
        assertThat(stored.get("origin")).isEqualTo("FAMILY");
        assertThat(stored.get("osm_type")).isNull();
        assertThat(stored.get("osm_id")).isNull();
        assertThat(stored.get("cnpj")).isEqualTo("12345678000195");
        assertThat(stored.get("website")).isEqualTo("https://familia.example.com");
    }

    @Test
    void convertActiveOsmIs409() {
        MarketsTestData.osm(jdbc, 1, "Mercado Exemplo 1", "-23.00100", "-46.00100");
        MvcTestResult archived = send(HttpMethod.PUT, "/markets/osm-n-1/links", noLinks());
        assertThat(archived).hasStatus(200);
        UUID row = jdbc.sql("select id from markets where workspace_id = :ws")
                .param("ws", ws)
                .query(UUID.class)
                .single();

        MvcTestResult result = send(
                HttpMethod.POST,
                "/markets/" + row + "/convert",
                "{\"name\":\"Mercado Exemplo\",\"kind\":\"SUPERMARKET\"}");

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("market.not_left");
        assertThat(jdbc.sql("select origin from markets where id = :id")
                        .param("id", row)
                        .query(String.class)
                        .single())
                .isEqualTo("OSM");
    }

    @Test
    void convertFamilyMarketIs409() {
        UUID own = MarketsTestData.family(jdbc, ws, "Feira Exemplo", null, null);

        MvcTestResult result = send(
                HttpMethod.POST, "/markets/" + own + "/convert", "{\"name\":\"Outro nome\",\"kind\":\"SUPERMARKET\"}");

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("market.not_left");
    }

    @Test
    void archiveTwiceIsNoop() {
        UUID own = MarketsTestData.family(jdbc, ws, "Feira Exemplo", "-23.00300", "-45.99900");

        MvcTestResult first = send(HttpMethod.POST, "/markets/" + own + "/archive", null);
        Timestamp archivedAt = archivedAt(own);
        MvcTestResult second = send(HttpMethod.POST, "/markets/" + own + "/archive", null);

        assertThat(first).hasStatus(204);
        assertThat(second).hasStatus(204);
        assertThat(archivedAt).isNotNull();
        assertThat(archivedAt(own)).isEqualTo(archivedAt);
        MvcTestResult search = send(HttpMethod.POST, "/markets/search", SEARCH);
        assertThat(search).bodyJson().extractingPath("$.family.length()").isEqualTo(0);
    }

    @Test
    void archiveOsmWithoutRowCreatesAndArchives() {
        MarketsTestData.osm(jdbc, 1, "Mercado Exemplo 1", "-23.00100", "-46.00100");

        MvcTestResult result = send(HttpMethod.POST, "/markets/osm-n-1/archive", null);

        assertThat(result).hasStatus(204);
        var row = jdbc.sql("select origin, archived_at is not null as archived from markets where workspace_id = :ws")
                .param("ws", ws)
                .query()
                .singleRow();
        assertThat(row.get("origin")).isEqualTo("OSM");
        assertThat(row.get("archived")).isEqualTo(true);
    }

    @Test
    void archiveUnknownOsmIs404() {
        MvcTestResult result = send(HttpMethod.POST, "/markets/osm-n-999/archive", null);

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("market.not_found");
    }

    private static String noLinks() {
        return "{\"website\":null,\"instagram\":null,\"flyerUrl\":null,\"flyerSource\":null,\"flyerValidUntil\":null}";
    }

    private Timestamp archivedAt(UUID id) {
        return jdbc.sql("select archived_at from markets where id = :id")
                .param("id", id)
                .query(Timestamp.class)
                .single();
    }

    private MvcTestResult send(HttpMethod method, String path, String body) {
        var session = TestUsers.signedIn(ana, null);
        var request = mvc.method(method)
                .uri("/v1/workspaces/" + ws + path)
                .with(session)
                .with(CsrfSession.start(mvc, session));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }
}
