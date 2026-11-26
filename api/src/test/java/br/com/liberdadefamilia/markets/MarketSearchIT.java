package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.markets.domain.OsmRef;
import br.com.liberdadefamilia.markets.internal.MarketRepository;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The search by area: what the family may send, the lists it gets back and what never shows. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MarketSearchIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    MarketRepository markets;

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
    void threeDecimalsIs422() {
        MvcTestResult result = search("{\"lat\":\"-10.001\",\"lon\":\"-50.00\"}");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("markets.location_too_precise");
    }

    @Test
    void latOutOfRangeIs422() {
        for (String body : List.of(
                "{\"lat\":\"95.00\",\"lon\":\"-50.00\"}",
                "{\"lat\":\"-10.00\",\"lon\":\"-181.00\"}",
                "{\"lat\":\"perto\",\"lon\":\"-50.00\"}")) {
            MvcTestResult result = search(body);

            assertThat(result).as(body).hasStatus(422);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("markets.location_invalid");
        }
    }

    @Test
    void radiusOutOfRangeIs422() {
        for (int radius : new int[] {0, 11}) {
            MvcTestResult result = search(body(MarketsTestData.CENTER_LAT, radius));

            assertThat(result).as("radius " + radius).hasStatus(422);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("markets.radius_invalid");
        }
    }

    @Test
    void withoutRadiusItUsesThree() {
        // The box is radius + 1 km: about 4 km by default, about 11 km at the largest radius.
        MarketsTestData.osm(jdbc, 1, "Perto", "-22.97000", "-46.00000"); // ~3.3 km
        MarketsTestData.osm(jdbc, 2, "Longe", "-22.95000", "-46.00000"); // ~5.6 km

        assertThat(search("{\"lat\":\"-23.00\",\"lon\":\"-46.00\"}"))
                .bodyJson()
                .extractingPath("$.osm.length()")
                .isEqualTo(1);
        assertThat(search("{\"lat\":\"-23.00\",\"lon\":\"-46.00\",\"radiusKm\":10}"))
                .bodyJson()
                .extractingPath("$.osm.length()")
                .isEqualTo(2);
    }

    @Test
    void returnsSeparateLists() {
        MarketsTestData.osm(jdbc, 1, "Mercado Exemplo 1", "-23.00100", "-46.00100");
        MarketsTestData.osm(jdbc, 2, "Mercado Exemplo 2", "-22.99800", "-46.00200");
        UUID own = MarketsTestData.family(jdbc, ws, "Feira Exemplo", "-23.00300", "-45.99900");

        MvcTestResult result = search(body(MarketsTestData.CENTER_LAT, null));

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.osm.length()").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.osm[0].ref").isEqualTo("osm-n-1");
        assertThat(result).bodyJson().extractingPath("$.osm[1].ref").isEqualTo("osm-n-2");
        assertThat(result).bodyJson().extractingPath("$.osm[0].familyId").isNull();
        assertThat(result).bodyJson().extractingPath("$.family.length()").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.family[0].ref").isEqualTo(own.toString());
        assertThat(result).bodyJson().extractingPath("$.family[0].name").isEqualTo("Feira Exemplo");
        assertThat(result).bodyJson().extractingPath("$.unlocated.length()").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.left.length()").isEqualTo(0);
    }

    @Test
    void coordinatesAreJsonStrings() {
        MarketsTestData.osm(jdbc, 1, "Mercado Exemplo 1", "-23.00100", "-46.00100");
        MarketsTestData.family(jdbc, ws, "Feira Exemplo", "-23.00300", "-45.99900");

        MvcTestResult result = search(body(MarketsTestData.CENTER_LAT, null));

        assertThat(result).bodyJson().extractingPath("$.osm[0].lat").isEqualTo("-23.00100");
        assertThat(result).bodyJson().extractingPath("$.osm[0].lon").isEqualTo("-46.00100");
        assertThat(result).bodyJson().extractingPath("$.family[0].lat").isEqualTo("-23.00300");
        assertThat(result).bodyJson().extractingPath("$.family[0].lon").isEqualTo("-45.99900");
    }

    @Test
    void familyEditShowsOnOsmItem() {
        MarketsTestData.osm(jdbc, 1, "Mercado Exemplo 1", "-23.00100", "-46.00100");
        UUID membership = workspaces.membershipOf(ws, ana);

        UUID row = markets.ensureOsmRow(ws, new OsmRef('n', 1), membership);

        MvcTestResult result = search(body(MarketsTestData.CENTER_LAT, null));
        assertThat(result).bodyJson().extractingPath("$.osm.length()").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.osm[0].ref").isEqualTo("osm-n-1");
        assertThat(result).bodyJson().extractingPath("$.osm[0].familyId").isEqualTo(row.toString());
        assertThat(result).bodyJson().extractingPath("$.family.length()").isEqualTo(0);
        assertThat(markets.ensureOsmRow(ws, new OsmRef('n', 1), membership)).isEqualTo(row);
    }

    @Test
    void capsAtFiveHundredNearestFirst() {
        jdbc.sql("""
                        insert into osm_markets (osm_type, osm_id, kind, lat, lon)
                        select 'n', i, 'SUPERMARKET', -23.00000 + i * 0.00003, -46.00000
                        from generate_series(1, 600) as i""").update();

        MvcTestResult result = search(body(MarketsTestData.CENTER_LAT, null));

        assertThat(result).bodyJson().extractingPath("$.osm.length()").isEqualTo(500);
        assertThat(result).bodyJson().extractingPath("$.osm[0].ref").isEqualTo("osm-n-1");
        assertThat(result).bodyJson().extractingPath("$.osm[499].ref").isEqualTo("osm-n-500");
    }

    @Test
    void leftAndUnlocatedSections() {
        UUID gone = MarketsTestData.familyRowForOsm(jdbc, ws, 777);
        UUID nowhere = MarketsTestData.family(jdbc, ws, "Mercado sem lugar", null, null);

        MvcTestResult result = search(body(MarketsTestData.CENTER_LAT, null));

        assertThat(result).bodyJson().extractingPath("$.left.length()").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.left[0].ref").isEqualTo(gone.toString());
        assertThat(result).bodyJson().extractingPath("$.left[0].cnpj").isEqualTo("12345678000195");
        assertThat(result).bodyJson().extractingPath("$.unlocated.length()").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.unlocated[0].ref").isEqualTo(nowhere.toString());
        assertThat(result).bodyJson().extractingPath("$.unlocated[0].name").isEqualTo("Mercado sem lugar");
        assertThat(result).bodyJson().extractingPath("$.family.length()").isEqualTo(0);
    }

    @Test
    void archivedNeverListed() {
        MarketsTestData.osm(jdbc, 1, "Mercado Exemplo 1", "-23.00100", "-46.00100");
        UUID osmRow = markets.ensureOsmRow(ws, new OsmRef('n', 1), workspaces.membershipOf(ws, ana));
        MarketsTestData.archive(jdbc, osmRow);
        MarketsTestData.archive(jdbc, MarketsTestData.family(jdbc, ws, "Feira Exemplo", "-23.00300", "-45.99900"));
        MarketsTestData.archive(jdbc, MarketsTestData.family(jdbc, ws, "Sem lugar", null, null));
        MarketsTestData.archive(jdbc, MarketsTestData.familyRowForOsm(jdbc, ws, 777));

        MvcTestResult result = search(body(MarketsTestData.CENTER_LAT, null));

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.osm.length()").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.family.length()").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.unlocated.length()").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.left.length()").isEqualTo(0);
    }

    @Test
    void otherFamiliesRowsStayApart() {
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID other = workspaces.createFor(bia);
        MarketsTestData.family(jdbc, other, "Feira de outra familia", "-23.00300", "-45.99900");
        MarketsTestData.familyRowForOsm(jdbc, other, 778);

        MvcTestResult result = search(body(MarketsTestData.CENTER_LAT, null));

        assertThat(result).bodyJson().extractingPath("$.family.length()").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.left.length()").isEqualTo(0);
    }

    @Test
    void datasetDateInSaoPaulo() {
        assertThat(search(body(MarketsTestData.CENTER_LAT, null)))
                .bodyJson()
                .extractingPath("$.datasetDate")
                .isNull();

        MarketsTestData.imported(jdbc, Instant.parse("2026-11-18T01:00:00Z"));

        assertThat(search(body(MarketsTestData.CENTER_LAT, null)))
                .bodyJson()
                .extractingPath("$.datasetDate")
                .isEqualTo("2026-11-17");
    }

    private static String body(String lat, Integer radius) {
        return "{\"lat\":\"" + lat + "\",\"lon\":\"" + MarketsTestData.CENTER_LON + "\""
                + (radius == null ? "" : ",\"radiusKm\":" + radius) + "}";
    }

    private MvcTestResult search(String body) {
        var session = TestUsers.signedIn(ana, null);
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/markets/search")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
