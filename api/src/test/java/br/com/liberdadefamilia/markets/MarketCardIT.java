package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.markets.domain.OsmRef;
import br.com.liberdadefamilia.markets.internal.MarketRepository;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The card of one market and the switches the app reads. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MarketCardIT {

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
    void osmCardWithoutFamilyRow() {
        MarketsTestData.osm(jdbc, 1, "Mercado Exemplo 1", "-23.00100", "-46.00100");
        MarketsTestData.imported(jdbc, Instant.parse("2026-11-18T15:00:00Z"));

        MvcTestResult result = get("osm-n-1");

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.ref").isEqualTo("osm-n-1");
        assertThat(result).bodyJson().extractingPath("$.origin").isEqualTo("OSM");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(result).bodyJson().extractingPath("$.familyId").isNull();
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Mercado Exemplo 1");
        assertThat(result).bodyJson().extractingPath("$.kind").isEqualTo("SUPERMARKET");
        assertThat(result).bodyJson().extractingPath("$.address.street").isEqualTo("Rua Exemplo");
        assertThat(result).bodyJson().extractingPath("$.datasetDate").isEqualTo("2026-11-18");
        assertThat(result).bodyJson().extractingPath("$.instagramEmbeddable").isEqualTo(false);
    }

    @Test
    void coordinatesAreJsonStrings() {
        MarketsTestData.osm(jdbc, 1, "Mercado Exemplo 1", "-23.00100", "-46.00100");
        UUID own = MarketsTestData.family(jdbc, ws, "Feira Exemplo", "-23.00300", "-45.99900");

        assertThat(get("osm-n-1")).bodyJson().extractingPath("$.lat").isEqualTo("-23.00100");
        assertThat(get("osm-n-1")).bodyJson().extractingPath("$.lon").isEqualTo("-46.00100");
        assertThat(get(own.toString())).bodyJson().extractingPath("$.lat").isEqualTo("-23.00300");
        assertThat(get(own.toString())).bodyJson().extractingPath("$.lon").isEqualTo("-45.99900");
    }

    @Test
    void familyCardByRowId() {
        UUID own = MarketsTestData.family(jdbc, ws, "Feira Exemplo", "-23.00300", "-45.99900");

        MvcTestResult result = get(own.toString());

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.origin").isEqualTo("FAMILY");
        assertThat(result).bodyJson().extractingPath("$.familyId").isEqualTo(own.toString());
        assertThat(result).bodyJson().extractingPath("$.ref").isEqualTo(own.toString());
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Feira Exemplo");
        assertThat(result).bodyJson().extractingPath("$.address").isNull();
    }

    @Test
    void familyLinksWinOverOpenStreetMap() {
        jdbc.sql("""
                        insert into osm_markets (osm_type, osm_id, kind, name, lat, lon, website, instagram)
                        values ('n', 1, 'SUPERMARKET', 'Mercado Exemplo 1', -23.001, -46.001,
                                'https://osm.example.com', 'https://www.instagram.com/osm_perfil/')""").update();
        UUID row = markets.ensureOsmRow(ws, new OsmRef('n', 1), workspaces.membershipOf(ws, ana));

        MvcTestResult before = get("osm-n-1");
        assertThat(before).bodyJson().extractingPath("$.website.source").isEqualTo("OSM");
        assertThat(before).bodyJson().extractingPath("$.website.value").isEqualTo("https://osm.example.com");
        assertThat(before).bodyJson().extractingPath("$.instagram.source").isEqualTo("OSM");

        jdbc.sql("""
                        update markets set website = 'https://familia.example.com', website_set_at = now(),
                               instagram = '@familia', instagram_set_at = now()
                        where id = :id""").param("id", row).update();

        MvcTestResult after = get("osm-n-1");
        assertThat(after).bodyJson().extractingPath("$.familyId").isEqualTo(row.toString());
        assertThat(after).bodyJson().extractingPath("$.website.source").isEqualTo("FAMILY");
        assertThat(after).bodyJson().extractingPath("$.website.value").isEqualTo("https://familia.example.com");
        assertThat(after).bodyJson().extractingPath("$.instagram.source").isEqualTo("FAMILY");
        assertThat(after)
                .bodyJson()
                .extractingPath("$.instagram.value")
                .isEqualTo("https://www.instagram.com/familia/");
        assertThat(get(row.toString())).bodyJson().extractingPath("$.ref").isEqualTo("osm-n-1");
    }

    @Test
    void familyLinkWinsWithItsDate() {
        MarketsTestData.imported(jdbc, Instant.parse("2026-11-18T15:00:00Z"));
        jdbc.sql("""
                        insert into osm_markets (osm_type, osm_id, kind, name, lat, lon, website)
                        values ('n', 1, 'SUPERMARKET', 'Mercado Exemplo 1', -23.001, -46.001, 'https://osm.example.com')""").update();
        UUID row = markets.ensureOsmRow(ws, new OsmRef('n', 1), workspaces.membershipOf(ws, ana));
        jdbc.sql("""
                        update markets set website = 'https://familia.example.com',
                               website_set_at = timestamptz '2026-11-20 01:30:00+00'
                        where id = :id""").param("id", row).update();

        MvcTestResult result = get("osm-n-1");

        assertThat(result).bodyJson().extractingPath("$.website.source").isEqualTo("FAMILY");
        // 01:30 UTC is still the 19th in America/Sao_Paulo.
        assertThat(result).bodyJson().extractingPath("$.website.date").isEqualTo("2026-11-19");
    }

    @Test
    void osmLinkCarriesDatasetDate() {
        MarketsTestData.imported(jdbc, Instant.parse("2026-11-18T15:00:00Z"));
        jdbc.sql("""
                        insert into osm_markets (osm_type, osm_id, kind, name, lat, lon, website)
                        values ('n', 1, 'SUPERMARKET', 'Mercado Exemplo 1', -23.001, -46.001, 'https://osm.example.com')""").update();

        MvcTestResult result = get("osm-n-1");

        assertThat(result).bodyJson().extractingPath("$.website.source").isEqualTo("OSM");
        assertThat(result).bodyJson().extractingPath("$.website.date").isEqualTo("2026-11-18");
        assertThat(result).bodyJson().extractingPath("$.datasetDate").isEqualTo("2026-11-18");
    }

    @Test
    void badOsmInstagramIsHidden() {
        jdbc.sql("""
                        insert into osm_markets (osm_type, osm_id, kind, name, lat, lon, instagram)
                        values ('n', 1, 'SUPERMARKET', 'Mercado Exemplo 1', -23.001, -46.001, 'não sei')""").update();

        MvcTestResult result = get("osm-n-1");

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.instagram").isNull();
        assertThat(result).bodyJson().extractingPath("$.instagramEmbeddable").isEqualTo(false);
    }

    @Test
    void moduleOffIs403() {
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID off = workspaces.createFor(bia);

        MvcTestResult result = mvc.get()
                .uri("/v1/workspaces/" + off + "/markets/osm-n-1")
                .with(TestUsers.signedIn(bia, null))
                .exchange();

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("module.disabled");
    }

    @Test
    void unsafeOsmWebsiteIsDropped() {
        jdbc.sql("""
                        insert into osm_markets (osm_type, osm_id, kind, name, lat, lon, website)
                        values ('n', 1, 'SUPERMARKET', 'Mercado Exemplo 1', -23.001, -46.001, 'javascript:alert(1)')""").update();

        assertThat(get("osm-n-1")).bodyJson().extractingPath("$.website").isNull();
    }

    @Test
    void leftOsmKeepsTheFamilysData() {
        UUID row = MarketsTestData.familyRowForOsm(jdbc, ws, 777);

        MvcTestResult result = get(row.toString());

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("LEFT_OSM");
        assertThat(result).bodyJson().extractingPath("$.ref").isEqualTo("osm-n-777");
        assertThat(result).bodyJson().extractingPath("$.cnpj").isEqualTo("12345678000195");
        assertThat(result).bodyJson().extractingPath("$.name").isNull();
    }

    @Test
    void archivedRowIsStillShownAsArchived() {
        UUID own = MarketsTestData.family(jdbc, ws, "Feira Exemplo", "-23.00300", "-45.99900");
        MarketsTestData.archive(jdbc, own);

        assertThat(get(own.toString())).bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
    }

    @Test
    void unknownOrForeignOrMalformedIs404() {
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID other = workspaces.createFor(bia);
        UUID foreign = MarketsTestData.family(jdbc, other, "Feira de outra familia", "-23.00300", "-45.99900");

        for (String ref :
                new String[] {"osm-n-999", foreign.toString(), UUID.randomUUID().toString(), "lixo"}) {
            MvcTestResult result = get(ref);

            assertThat(result).as(ref).hasStatus(404);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("market.not_found");
        }
    }

    @Test
    void settingsShowTheSwitches() {
        MvcTestResult result = settings();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.geocodingEnabled").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.instagramEnabled").isEqualTo(true);
    }

    /** Embed is on, profile embeds are off: a post embeds, a profile does not. */
    @Test
    void embeddableFollowsTheGate() {
        assertThat(embeddable("https://www.instagram.com/p/Abc123/")).isTrue();
        assertThat(embeddable("@perfil")).isFalse();
    }

    @Nested
    @TestPropertySource(properties = "liberdade.markets.instagram.profile-embeds=true")
    class ProfileEmbedsOn {

        @Test
        void embeddableFollowsTheGate() {
            assertThat(embeddable("https://www.instagram.com/p/Abc123/")).isTrue();
            assertThat(embeddable("@perfil")).isTrue();
        }
    }

    @Nested
    @TestPropertySource(properties = "liberdade.markets.instagram.enabled=false")
    class InstagramOff {

        @Test
        void embeddableFollowsTheGate() {
            assertThat(embeddable("https://www.instagram.com/p/Abc123/")).isFalse();
            assertThat(embeddable("@perfil")).isFalse();
            assertThat(settings())
                    .bodyJson()
                    .extractingPath("$.instagramEnabled")
                    .isEqualTo(false);
        }
    }

    private boolean embeddable(String instagram) {
        UUID own = MarketsTestData.family(jdbc, ws, "Feira Exemplo", "-23.00300", "-45.99900");
        jdbc.sql("update markets set instagram = :i, instagram_set_at = now() where id = :id")
                .param("i", instagram)
                .param("id", own)
                .update();
        MvcTestResult result = get(own.toString());
        assertThat(result).hasStatus(200);
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.instagramEmbeddable");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult settings() {
        return mvc.get()
                .uri("/v1/workspaces/" + ws + "/markets/settings")
                .with(TestUsers.signedIn(ana, null))
                .exchange();
    }

    private MvcTestResult get(String ref) {
        return mvc.get()
                .uri("/v1/workspaces/" + ws + "/markets/" + ref)
                .with(TestUsers.signedIn(ana, null))
                .exchange();
    }
}
