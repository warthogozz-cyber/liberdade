package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.markets.domain.KeyHash;
import br.com.liberdadefamilia.markets.domain.NfceKey;
import br.com.liberdadefamilia.markets.domain.NfceKeyFixtures;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.time.YearMonth;
import java.util.UUID;
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

/** The lookup of an NFC-e key: the key rules, the {@code p} rules, the duplicate and the market of the CNPJ. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReceiptLookupIT {

    private static final String KEY = NfceKeyFixtures.make("43", "2611");
    private static final String CNPJ = NfceKeyFixtures.cnpj(NfceKeyFixtures.CNPJ_BASE);

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
    void manualWithoutReader() {
        MvcTestResult result = lookup(KEY, 0, null);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.mode").isEqualTo("MANUAL");
        assertThat(result).bodyJson().extractingPath("$.uf").isEqualTo("RS");
        assertThat(result).bodyJson().extractingPath("$.issuedMonth").isEqualTo("2026-11");
        assertThat(result).bodyJson().extractingPath("$.market").isNull();
        assertThat(result).bodyJson().extractingPath("$.items").asList().isEmpty();
    }

    @Test
    void keyWithSpacesAndDotsIsAccepted() {
        String typed = KEY.replaceAll("(.{4})", "$1 ").strip().replaceFirst(" ", ".");

        assertThat(lookup(typed, 0, null)).hasStatus(200);
    }

    @Test
    void findsMarketByCnpj() {
        UUID market = marketWithCnpj("Mercado Exemplo");

        MvcTestResult result = lookup(KEY, 1, null);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.market.familyId").isEqualTo(market.toString());
        assertThat(result).bodyJson().extractingPath("$.market.ref").isEqualTo(market.toString());
        assertThat(result).bodyJson().extractingPath("$.market.name").isEqualTo("Mercado Exemplo");
        assertThat(result).bodyJson().extractingPath("$.market.kind").isEqualTo("GREENGROCER");
        assertThat(result).bodyJson().extractingPath("$.market.status").isEqualTo("ACTIVE");
    }

    @Test
    void archivedMarketStillMatches() {
        UUID market = marketWithCnpj("Mercado Exemplo");
        MarketsTestData.archive(jdbc, market);

        MvcTestResult result = lookup(KEY, 0, null);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.market.familyId").isEqualTo(market.toString());
        assertThat(result).bodyJson().extractingPath("$.market.status").isEqualTo("ARCHIVED");
    }

    @Test
    void marketOfAnotherFamilyDoesNotMatch() {
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID other = workspaces.createFor(bia);
        jdbc.sql("""
                        insert into markets (workspace_id, origin, name, kind, cnpj)
                        values (:ws, 'FAMILY', 'Outro', 'GREENGROCER', :cnpj)""").param("ws", other).param("cnpj", CNPJ).update();

        assertThat(lookup(KEY, 0, null)).bodyJson().extractingPath("$.market").isNull();
    }

    @Test
    void savedKeyIs409() {
        UUID market = marketWithCnpj("Mercado Exemplo");
        jdbc.sql("""
                        insert into receipts (workspace_id, market_id, key_hash, issued_on, mode, item_count)
                        values (:ws, :market, :hash, '2026-11-10', 'MANUAL', 1)""")
                .param("ws", ws)
                .param("market", market)
                .param("hash", KeyHash.of(ws, NfceKey.parse(KEY, YearMonth.of(2026, 12))))
                .update();

        MvcTestResult result = lookup(KEY, 0, null);

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("receipt.duplicate");
    }

    @Test
    void badKeyIs422() {
        MvcTestResult result = lookup(NfceKeyFixtures.withWrongCheckDigit(KEY), 0, null);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("receipt.key_invalid");
        assertThat(result).bodyText().doesNotContain(KEY.substring(0, 20));
    }

    @Test
    void shortOrMissingKeyIs422() {
        assertThat(lookup("123", 0, null)).hasStatus(422);
        assertThat(lookup("", 0, null)).hasStatus(422);
        assertThat(lookup(KEY, 4, null)).hasStatus(422);
        assertThat(lookup(KEY, -1, null)).hasStatus(422);
    }

    @Test
    void v2NeedsProductionP() {
        String online = KEY + "|2|1|1";

        assertThat(lookup(KEY, 2, null)).hasStatus(422);
        assertThat(lookup(KEY, 2, NfceKeyFixtures.make("43", "2610") + "|2|1|1"))
                .hasStatus(422);
        assertThat(lookup(KEY, 2, KEY + "|2|2|1")).hasStatus(422);
        assertThat(lookup(KEY, 2, online + "x".repeat(300))).hasStatus(422);
        assertThat(lookup(KEY, 2, online + "|" + "A".repeat(40))).hasStatus(200);
        assertThat(lookup(KEY, 2, online)).hasStatus(200);
    }

    @Test
    void v3OfflineFormIs422() {
        String offline = KEY + "|3|1|15|10.00|1|12345678909|c2lnbmF0dXJl";

        MvcTestResult result = lookup(KEY, 3, offline);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("receipt.key_invalid");
        assertThat(result).bodyText().doesNotContain("12345678909");
        assertThat(lookup(KEY, 3, KEY + "|3|1")).hasStatus(200);
        assertThat(lookup(KEY, 3, null)).hasStatus(422);
        assertThat(lookup(KEY, 3, KEY + "|3|2")).hasStatus(422);
    }

    @Test
    void v1TakesNoP() {
        assertThat(lookup(KEY, 1, KEY + "|2|1|1")).hasStatus(422);
        assertThat(lookup(KEY, 1, null)).hasStatus(200);
    }

    @Test
    void typedKeyHasNoP() {
        assertThat(lookup(KEY, 0, KEY + "|3|1")).hasStatus(422);
        assertThat(lookup(KEY, 0, "")).hasStatus(422);
    }

    @Test
    void writesNothing() {
        marketWithCnpj("Mercado Exemplo");
        long markets = count("markets");

        lookup(KEY, 0, null);
        lookup(KEY, 1, null);
        lookup(KEY, 3, KEY + "|3|1");

        assertThat(count("receipts")).isZero();
        assertThat(count("products")).isZero();
        assertThat(count("price_records")).isZero();
        assertThat(count("markets")).isEqualTo(markets);
    }

    @Test
    void moduleOffIs403() {
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID off = workspaces.createFor(bia);
        var session = TestUsers.signedIn(bia, null);

        MvcTestResult result = mvc.post()
                .uri("/v1/workspaces/" + off + "/markets/receipts/lookup")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(KEY, 0, null))
                .exchange();

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("module.disabled");
    }

    private UUID marketWithCnpj(String name) {
        return jdbc.sql("""
                        insert into markets (workspace_id, origin, name, kind, cnpj)
                        values (:ws, 'FAMILY', :name, 'GREENGROCER', :cnpj) returning id""")
                .param("ws", ws)
                .param("name", name)
                .param("cnpj", CNPJ)
                .query(UUID.class)
                .single();
    }

    private long count(String table) {
        return jdbc.sql("select count(*) from " + table + " where workspace_id = :ws")
                .param("ws", ws)
                .query(Long.class)
                .single();
    }

    private MvcTestResult lookup(String chave, int version, String p) {
        var session = TestUsers.signedIn(ana, null);
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/markets/receipts/lookup")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(chave, version, p))
                .exchange();
    }

    static String body(String chave, int version, String p) {
        return "{\"chave\":\"" + chave + "\",\"version\":" + version + (p == null ? "" : ",\"p\":\"" + p + "\"") + "}";
    }
}
