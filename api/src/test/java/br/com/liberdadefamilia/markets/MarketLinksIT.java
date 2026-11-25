package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.sql.Timestamp;
import java.util.List;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The family's links on a market card: website, Instagram and flyer. */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
class MarketLinksIT {

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
        MarketsTestData.clearExtract(jdbc);
        doReturn(false).when(plans).legalSetEnabled();
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'markets', true)")
                .param("ws", ws)
                .update();
        events.clear();
    }

    @AfterEach
    void clean() {
        MarketsTestData.clearExtract(jdbc);
    }

    @Test
    void firstEditCreatesTheOsmRow() {
        MarketsTestData.osm(jdbc, 1, "Mercado Exemplo 1", "-23.00100", "-46.00100");

        MvcTestResult result = links("osm-n-1", "https://familia.example.com", null, null, null, null);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.ref").isEqualTo("osm-n-1");
        assertThat(result).bodyJson().extractingPath("$.website.source").isEqualTo("FAMILY");
        var row = jdbc.sql("select origin, website from markets where workspace_id = :ws")
                .param("ws", ws)
                .query()
                .singleRow();
        assertThat(row.get("origin")).isEqualTo("OSM");
        assertThat(row.get("website")).isEqualTo("https://familia.example.com");
        assertThat(search()).bodyJson().extractingPath("$.osm[0].familyId").isNotNull();
    }

    @Test
    void linksOnUnknownOsmRefIs404() {
        MvcTestResult result = links("osm-n-999", "https://familia.example.com", null, null, null, null);

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("market.not_found");
        assertThat(jdbc.sql("select count(*) from markets where workspace_id = :ws")
                        .param("ws", ws)
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void setAtOnlyForChangedField() {
        UUID own = MarketsTestData.family(jdbc, ws, "Feira Exemplo", "-23.00300", "-45.99900");
        links(own.toString(), "https://familia.example.com", null, "https://encarte.example.com/a", "site", null);
        jdbc.sql("""
                        update markets set website_set_at = timestamptz '2026-01-01 12:00:00+00',
                               flyer_set_at = timestamptz '2026-01-01 12:00:00+00'
                        where id = :id""").param("id", own).update();

        MvcTestResult result = links(
                own.toString(), "https://familia.example.com", null, "https://encarte.example.com/b", "site", null);

        assertThat(result).hasStatus(200);
        var row = jdbc.sql("select website_set_at, flyer_set_at from markets where id = :id")
                .param("id", own)
                .query()
                .singleRow();
        assertThat(((Timestamp) row.get("website_set_at")).toInstant().toString())
                .isEqualTo("2026-01-01T12:00:00Z");
        assertThat(((Timestamp) row.get("flyer_set_at")).toInstant().toString()).isNotEqualTo("2026-01-01T12:00:00Z");
    }

    @Test
    void nullFallsBackToOsm() {
        jdbc.sql("""
                        insert into osm_markets (osm_type, osm_id, kind, name, lat, lon, website)
                        values ('n', 1, 'SUPERMARKET', 'Mercado Exemplo 1', -23.001, -46.001, 'https://osm.example.com')""").update();
        links("osm-n-1", "https://familia.example.com", null, null, null, null);

        MvcTestResult result = links("osm-n-1", null, null, null, null, null);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.website.source").isEqualTo("OSM");
        assertThat(result).bodyJson().extractingPath("$.website.value").isEqualTo("https://osm.example.com");
        assertThat(jdbc.sql("select website_set_at from markets where workspace_id = :ws")
                        .param("ws", ws)
                        .query(Timestamp.class)
                        .optional())
                .isEmpty();
    }

    @Test
    void rejectsHttpAndBadInstagram() {
        UUID own = MarketsTestData.family(jdbc, ws, "Feira Exemplo", "-23.00300", "-45.99900");

        for (String bad : List.of("http://familia.example.com", "https://u:p@familia.example.com", "javascript:x")) {
            MvcTestResult website = links(own.toString(), bad, null, null, null, null);
            assertThat(website).as(bad).hasStatus(422);
            assertThat(website).bodyJson().extractingPath("$.code").isEqualTo("markets.link_invalid");
        }
        MvcTestResult flyer = links(own.toString(), null, null, "http://encarte.example.com", "site", null);
        assertThat(flyer).hasStatus(422);
        assertThat(flyer).bodyJson().extractingPath("$.code").isEqualTo("markets.link_invalid");
        MvcTestResult instagram = links(own.toString(), null, "isto nao e um perfil!", null, null, null);
        assertThat(instagram).hasStatus(422);
        assertThat(instagram).bodyJson().extractingPath("$.code").isEqualTo("markets.instagram_invalid");
    }

    @Test
    void instagramIsStoredCanonical() {
        UUID own = MarketsTestData.family(jdbc, ws, "Feira Exemplo", "-23.00300", "-45.99900");

        MvcTestResult result = links(own.toString(), null, "@Familia", null, null, null);

        assertThat(result).hasStatus(200);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.instagram.value")
                .isEqualTo("https://www.instagram.com/familia/");
        assertThat(jdbc.sql("select instagram from markets where id = :id")
                        .param("id", own)
                        .query(String.class)
                        .single())
                .isEqualTo("https://www.instagram.com/familia/");
    }

    @Test
    void flyerNeedsSource() {
        UUID own = MarketsTestData.family(jdbc, ws, "Feira Exemplo", "-23.00300", "-45.99900");

        MvcTestResult result = links(own.toString(), null, null, "https://encarte.example.com", null, null);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("markets.flyer_source_required");
    }

    @Test
    void flyerIsKeptWithItsSourceAndDate() {
        UUID own = MarketsTestData.family(jdbc, ws, "Feira Exemplo", "-23.00300", "-45.99900");

        MvcTestResult result =
                links(own.toString(), null, null, "https://encarte.example.com", "site do mercado", "2026-12-01");

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.flyer.url").isEqualTo("https://encarte.example.com");
        assertThat(result).bodyJson().extractingPath("$.flyer.source").isEqualTo("site do mercado");
        assertThat(result).bodyJson().extractingPath("$.flyer.validUntil").isEqualTo("2026-12-01");
    }

    @Test
    void eventsCarryNoValues() {
        UUID own = MarketsTestData.family(jdbc, ws, "Feira Exemplo", "-23.00300", "-45.99900");

        links(own.toString(), "https://familia.example.com", "@familia", "https://encarte.example.com", "site", null);

        List<MarketEdited> edited = events.stream(MarketEdited.class).toList();
        assertThat(edited).hasSize(1);
        assertThat(edited.get(0).change()).isEqualTo(MarketEdited.Change.LINKS);
        assertThat(edited.get(0).marketId()).isEqualTo(own);
        assertThat(edited.get(0).workspaceId()).isEqualTo(ws);
        assertThat(edited.get(0).actorUserId()).isEqualTo(ana);
        assertThat(edited.get(0).toString()).doesNotContain("example.com").doesNotContain("familia");
    }

    private MvcTestResult search() {
        var session = TestUsers.signedIn(ana, null);
        return mvc.method(HttpMethod.POST)
                .uri("/v1/workspaces/" + ws + "/markets/search")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lat\":\"-23.00\",\"lon\":\"-46.00\"}")
                .exchange();
    }

    private MvcTestResult links(
            String ref, String website, String instagram, String flyerUrl, String flyerSource, String validUntil) {
        var session = TestUsers.signedIn(ana, null);
        String body = "{\"website\":%s,\"instagram\":%s,\"flyerUrl\":%s,\"flyerSource\":%s,\"flyerValidUntil\":%s}"
                .formatted(json(website), json(instagram), json(flyerUrl), json(flyerSource), json(validUntil));
        return mvc.method(HttpMethod.PUT)
                .uri("/v1/workspaces/" + ws + "/markets/" + ref + "/links")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
