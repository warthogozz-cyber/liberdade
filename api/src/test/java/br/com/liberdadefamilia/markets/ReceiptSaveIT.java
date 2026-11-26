package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.markets.domain.NfceKeyFixtures;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Saving a receipt: the market, the receipt, the products and the prices, all or nothing. */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@ExtendWith(OutputCaptureExtension.class)
@Import({TestcontainersConfiguration.class, ReceiptSaveIT.Config.class})
class ReceiptSaveIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    private static final String KEY = NfceKeyFixtures.make("43", "2611");
    private static final String CNPJ = NfceKeyFixtures.cnpj(NfceKeyFixtures.CNPJ_BASE);
    private static final String OTHER_CNPJ = NfceKeyFixtures.cnpj("123456780002");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestClock clock;

    @Autowired
    ApplicationEvents events;

    @MockitoSpyBean
    Plans plans;

    private UUID ana;
    private UUID ws;

    @BeforeEach
    void family() {
        doReturn(false).when(plans).legalSetEnabled();
        clock.freeze(Instant.parse("2026-11-20T15:00:00Z"));
        events.clear();
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'markets', true)")
                .param("ws", ws)
                .update();
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void savesReceiptAndPrices() {
        UUID market = familyMarket("Mercado Exemplo", CNPJ);

        MvcTestResult result = save(body(KEY, "2026-11-10", market.toString(), null, item("Arroz 5kg", "1", "4.99")));

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.marketRef").isEqualTo(market.toString());
        assertThat(result).bodyJson().extractingPath("$.prices").isEqualTo(1);
        assertThat(count("receipts")).isEqualTo(1);
        assertThat(count("products")).isEqualTo(1);
        assertThat(count("price_records")).isEqualTo(1);
        var row = jdbc.sql("""
                        select r.market_id, r.mode, r.item_count, p.unit_price::text as price, p.unit
                        from receipts r join price_records p on p.receipt_id = r.id
                        where r.workspace_id = :ws""").param("ws", ws).query().singleRow();
        assertThat(row.get("market_id")).isEqualTo(market);
        assertThat(row.get("mode")).isEqualTo("MANUAL");
        assertThat(row.get("item_count")).isEqualTo(1);
        assertThat(row.get("unit")).isEqualTo("UN");
        assertThat(row.get("price")).isEqualTo("4.9900");
    }

    @Test
    void osmMarketGetsARowAndTheCnpj() {
        MarketsTestData.osm(jdbc, 1, "Mercado Exemplo 1", "-23.00100", "-46.00100");

        MvcTestResult result = save(body(KEY, "2026-11-10", "osm-n-1", null, item("Feijao", "1", "7.50")));

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.marketRef").isEqualTo("osm-n-1");
        var row = jdbc.sql("select origin, cnpj from markets where workspace_id = :ws and osm_id = 1")
                .param("ws", ws)
                .query()
                .singleRow();
        assertThat(row.get("origin")).isEqualTo("OSM");
        assertThat(row.get("cnpj")).isEqualTo(CNPJ);
        assertThat(events.stream(MarketEdited.class).map(MarketEdited::change)).contains(MarketEdited.Change.CNPJ);
    }

    @Test
    void newMarketIsFamilyWithoutPosition() {
        MvcTestResult result = save(body(KEY, "2026-11-10", null, "Mercadinho Novo", item("Leite", "1", "5.00")));

        assertThat(result).hasStatus(201);
        var row = jdbc.sql("select origin, name, kind, cnpj, lat, lon from markets where workspace_id = :ws")
                .param("ws", ws)
                .query()
                .singleRow();
        assertThat(row.get("origin")).isEqualTo("FAMILY");
        assertThat(row.get("name")).isEqualTo("Mercadinho Novo");
        assertThat(row.get("kind")).isEqualTo("GREENGROCER");
        assertThat(row.get("cnpj")).isEqualTo(CNPJ);
        assertThat(row.get("lat")).isNull();
        assertThat(row.get("lon")).isNull();
    }

    @Test
    void marketWithOtherCnpjIs409() {
        UUID market = familyMarket("Mercado Exemplo", OTHER_CNPJ);

        MvcTestResult result = save(body(KEY, "2026-11-10", market.toString(), null, item("Arroz", "1", "4.99")));

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("receipt.market_cnpj_differs");
        assertThat(count("receipts")).isZero();
    }

    @Test
    void cnpjOnAnotherMarketIs409() {
        familyMarket("Dono do CNPJ", CNPJ);
        UUID without = familyMarket("Sem CNPJ", null);

        MvcTestResult result = save(body(KEY, "2026-11-10", without.toString(), null, item("Arroz", "1", "4.99")));

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("market.cnpj_taken");
        assertThat(count("receipts")).isZero();
    }

    @Test
    void bothOrNeitherMarketIs422() {
        UUID market = familyMarket("Mercado Exemplo", CNPJ);
        String items = "[" + item("Arroz", "1", "4.99") + "]";

        MvcTestResult both = save(bodyWith(
                KEY,
                "2026-11-10",
                "{\"ref\":\"" + market + "\"}",
                "{\"name\":\"Novo\",\"kind\":\"GREENGROCER\"}",
                items));
        MvcTestResult neither = save(bodyWith(KEY, "2026-11-10", null, null, items));

        assertThat(both).hasStatus(422);
        assertThat(both).bodyJson().extractingPath("$.code").isEqualTo("receipt.market_required");
        assertThat(neither).hasStatus(422);
        assertThat(neither).bodyJson().extractingPath("$.code").isEqualTo("receipt.market_required");
    }

    @Test
    void sameNewNameTwiceIsOneProduct() {
        UUID market = familyMarket("Mercado Exemplo", CNPJ);

        MvcTestResult result = save(body(
                KEY,
                "2026-11-10",
                market.toString(),
                null,
                item("Arroz 5kg", "1", "4.99"),
                item("  ARROZ   5kg ", "2", "5.10")));

        assertThat(result).hasStatus(201);
        assertThat(count("products")).isEqualTo(1);
        assertThat(count("product_aliases")).isEqualTo(1);
        assertThat(count("price_records")).isEqualTo(2);
    }

    @Test
    void existingAliasReusesProduct() {
        UUID market = familyMarket("Mercado Exemplo", CNPJ);
        UUID product = product("Arroz");
        jdbc.sql("""
                        insert into product_aliases (workspace_id, market_id, description_norm, product_id)
                        values (:ws, :market, 'arroz 5kg', :product)""")
                .param("ws", ws)
                .param("market", market)
                .param("product", product)
                .update();

        MvcTestResult result = save(body(KEY, "2026-11-10", market.toString(), null, item("Arroz 5KG", "1", "4.99")));

        assertThat(result).hasStatus(201);
        assertThat(count("products")).isEqualTo(1);
        assertThat(jdbc.sql("select product_id from price_records where workspace_id = :ws")
                        .param("ws", ws)
                        .query(UUID.class)
                        .single())
                .isEqualTo(product);
    }

    @Test
    void foreignProductIs422() {
        UUID market = familyMarket("Mercado Exemplo", CNPJ);
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID other = workspaces.createFor(bia);
        UUID foreign = jdbc.sql(
                        "insert into products (workspace_id, name, unit) values (:ws, 'Alheio', 'UN') returning id")
                .param("ws", other)
                .query(UUID.class)
                .single();
        String items = "[{\"productId\":\"" + foreign + "\",\"unit\":\"UN\",\"unitPrice\":\"4.99\"}]";

        MvcTestResult result = save(bodyWith(KEY, "2026-11-10", "{\"ref\":\"" + market + "\"}", null, items));

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("receipt.product_unknown");
        assertThat(count("receipts")).isZero();
    }

    @Test
    void fiveDecimalsIs422() {
        UUID market = familyMarket("Mercado Exemplo", CNPJ);

        for (String price : new String[] {"4.99001", "0", "-1", "4,99"}) {
            MvcTestResult result = save(body(KEY, "2026-11-10", market.toString(), null, item("Arroz", "1", price)));

            assertThat(result).hasStatus(422);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("receipt.price_invalid");
        }
        assertThat(count("receipts")).isZero();
    }

    @Test
    void fourDecimalsKept() {
        UUID market = familyMarket("Mercado Exemplo", CNPJ);

        MvcTestResult result = save(body(KEY, "2026-11-10", market.toString(), null, item("Arroz", "1", "4.9912")));

        assertThat(result).hasStatus(201);
        assertThat(jdbc.sql("select unit_price::text from price_records where workspace_id = :ws")
                        .param("ws", ws)
                        .query(String.class)
                        .single())
                .isEqualTo("4.9912");
    }

    @Test
    void dateOutsideKeyMonthIs422() {
        UUID market = familyMarket("Mercado Exemplo", CNPJ);

        for (String date : new String[] {"2026-10-31", "2026-12-01"}) {
            MvcTestResult result = save(body(KEY, date, market.toString(), null, item("Arroz", "1", "4.99")));

            assertThat(result).hasStatus(422);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("receipt.date_invalid");
        }
    }

    @Test
    void tomorrowIs422() {
        UUID market = familyMarket("Mercado Exemplo", CNPJ);

        assertThat(save(body(KEY, "2026-11-21", market.toString(), null, item("Arroz", "1", "4.99"))))
                .hasStatus(422);
        assertThat(save(body(KEY, "2026-11-20", market.toString(), null, item("Arroz", "1", "4.99"))))
                .hasStatus(201);
    }

    @Test
    void itemsLimits() {
        UUID market = familyMarket("Mercado Exemplo", CNPJ);
        String ref = "{\"ref\":\"" + market + "\"}";
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            many.add(item("Produto " + i, "1", "1.00"));
        }

        MvcTestResult none = save(bodyWith(KEY, "2026-11-10", ref, null, "[]"));
        MvcTestResult tooMany = save(bodyWith(KEY, "2026-11-10", ref, null, "[" + String.join(",", many) + "]"));

        assertThat(none).hasStatus(422);
        assertThat(none).bodyJson().extractingPath("$.code").isEqualTo("receipt.items_invalid");
        assertThat(tooMany).hasStatus(422);
        assertThat(tooMany).bodyJson().extractingPath("$.code").isEqualTo("receipt.items_invalid");
        assertThat(count("receipts")).isZero();
    }

    @Test
    void secondSaveIs409() {
        UUID market = familyMarket("Mercado Exemplo", CNPJ);
        String body = body(KEY, "2026-11-10", market.toString(), null, item("Arroz", "1", "4.99"));

        assertThat(save(body)).hasStatus(201);
        MvcTestResult second = save(body);

        assertThat(second).hasStatus(409);
        assertThat(second).bodyJson().extractingPath("$.code").isEqualTo("receipt.duplicate");
        assertThat(count("receipts")).isEqualTo(1);
        assertThat(count("price_records")).isEqualTo(1);
    }

    @Test
    void concurrentSavesOneWins() throws Exception {
        UUID market = familyMarket("Mercado Exemplo", CNPJ);
        String body = body(KEY, "2026-11-10", market.toString(), null, item("Arroz", "1", "4.99"));
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> calls =
                    Collections.nCopies(4, () -> save(body).getResponse().getStatus());
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : pool.invokeAll(calls)) {
                statuses.add(future.get());
            }
            assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
            assertThat(statuses.stream().filter(s -> s == 409)).hasSize(3);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("receipts")).isEqualTo(1);
        assertThat(count("price_records")).isEqualTo(1);
        assertThat(count("products")).isEqualTo(1);
    }

    @Test
    void failedSaveLeavesNothing() {
        // The second item fails after the market, the receipt and the first price were written.
        String unknown = "{\"productId\":\"" + UUID.randomUUID() + "\",\"unit\":\"UN\",\"unitPrice\":\"1.00\"}";

        MvcTestResult result =
                save(body(KEY, "2026-11-10", null, "Mercadinho Novo", item("Arroz", "1", "4.99"), unknown));

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("receipt.product_unknown");
        assertThat(count("markets")).isZero();
        assertThat(count("receipts")).isZero();
        assertThat(count("products")).isZero();
        assertThat(count("product_aliases")).isZero();
        assertThat(count("price_records")).isZero();
        assertThat(events.stream(ReceiptAdded.class)).isEmpty();
    }

    @Test
    void logsCarryNoReceiptData(CapturedOutput output) {
        UUID market = familyMarket("Mercado Exemplo", CNPJ);
        String p = KEY + "|2|1|1|12345678909";
        String good = body(KEY, "2026-11-10", market.toString(), null, item("Produto Secreto", "1", "4.9876"));

        save(good.replace("\"version\":0", "\"version\":2,\"p\":\"" + p + "\""));
        save(body(
                NfceKeyFixtures.withWrongCheckDigit(KEY),
                "2026-11-10",
                market.toString(),
                null,
                item("Produto Secreto", "1", "4.9876")));

        assertThat(output.getAll())
                .doesNotContain(KEY.substring(0, 30))
                .doesNotContain(CNPJ)
                .doesNotContain("12345678909")
                .doesNotContain("Produto Secreto")
                .doesNotContain("4.9876");
    }

    @Test
    void receiptAddedPublished() {
        UUID market = familyMarket("Mercado Exemplo", CNPJ);

        MvcTestResult result = save(body(
                KEY, "2026-11-10", market.toString(), null, item("Arroz", "1", "4.99"), item("Feijao", "1", "7.50")));

        assertThat(result).hasStatus(201);
        UUID receipt = jdbc.sql("select id from receipts where workspace_id = :ws")
                .param("ws", ws)
                .query(UUID.class)
                .single();
        List<ReceiptAdded> added = events.stream(ReceiptAdded.class).toList();
        assertThat(added).containsExactly(new ReceiptAdded(ws, receipt, ana, "MANUAL", 2));
        assertThat(added.get(0).toString()).doesNotContain(KEY).doesNotContain(CNPJ);
    }

    private UUID familyMarket(String name, String cnpj) {
        return jdbc.sql("""
                        insert into markets (workspace_id, origin, name, kind, cnpj)
                        values (:ws, 'FAMILY', :name, 'GREENGROCER', :cnpj) returning id""")
                .param("ws", ws)
                .param("name", name)
                .param("cnpj", cnpj, Types.VARCHAR)
                .query(UUID.class)
                .single();
    }

    private UUID product(String name) {
        return jdbc.sql("insert into products (workspace_id, name, unit) values (:ws, :name, 'UN') returning id")
                .param("ws", ws)
                .param("name", name)
                .query(UUID.class)
                .single();
    }

    private long count(String table) {
        return jdbc.sql("select count(*) from " + table + " where workspace_id = :ws")
                .param("ws", ws)
                .query(Long.class)
                .single();
    }

    private static String item(String name, String quantity, String price) {
        return "{\"newProductName\":\"" + name + "\",\"unit\":\"UN\",\"quantity\":\"" + quantity + "\",\"unitPrice\":\""
                + price + "\"}";
    }

    /** Either {@code ref} (an existing market) or {@code newName} (a new one) is given. */
    private static String body(String chave, String issuedOn, String ref, String newName, String... items) {
        String market = ref == null ? null : "{\"ref\":\"" + ref + "\"}";
        String created = newName == null ? null : "{\"name\":\"" + newName + "\",\"kind\":\"GREENGROCER\"}";
        return bodyWith(chave, issuedOn, market, created, "[" + String.join(",", items) + "]");
    }

    private static String bodyWith(String chave, String issuedOn, String market, String newMarket, String items) {
        StringBuilder json = new StringBuilder("{\"chave\":\"").append(chave).append("\",\"version\":0");
        json.append(",\"issuedOn\":\"").append(issuedOn).append('"');
        if (market != null) {
            json.append(",\"market\":").append(market);
        }
        if (newMarket != null) {
            json.append(",\"newMarket\":").append(newMarket);
        }
        return json.append(",\"items\":").append(items).append('}').toString();
    }

    private MvcTestResult save(String json) {
        var session = TestUsers.signedIn(ana, null);
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/markets/receipts")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }
}
