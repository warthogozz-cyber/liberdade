package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The shopping list at two or three markets. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, CompareIT.Config.class})
class CompareIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

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

    @MockitoSpyBean
    Plans plans;

    private UUID ana;
    private UUID ws;
    private UUID marketA;
    private UUID marketB;

    @BeforeEach
    void family() {
        doReturn(false).when(plans).legalSetEnabled();
        clock.freeze(Instant.parse("2026-11-24T15:00:00Z"));
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'markets', true)")
                .param("ws", ws)
                .update();
        marketA = MarketsTestData.family(jdbc, ws, "Mercado Exemplo A", null, null);
        marketB = MarketsTestData.family(jdbc, ws, "Mercado Exemplo B", null, null);
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void missingAndMismatchCounted() {
        UUID milk = listed("Leite Exemplo 1 L", "2");
        UUID rice = listed("Arroz Exemplo 5 kg", "1");
        UUID beans = listed("Feijão Exemplo", "1");
        UUID coffee = listed("Café Exemplo", "1");
        price(marketA, milk, "2026-11-20", "4.5000", "UN");
        price(marketB, milk, "2026-11-20", "5.5000", "UN");
        price(marketA, rice, "2026-11-20", "20.0000", "UN");
        price(marketB, rice, "2026-11-20", "3.9000", "KG");
        price(marketA, beans, "2026-11-20", "8.0000", "UN");
        price(marketA, coffee, "2026-11-20", "15.0000", "UN");
        price(marketB, coffee, "2026-11-20", "14.0000", "UN");

        MvcTestResult result = compare(marketA + "," + marketB);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.commonCount").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.unitMismatchCount").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.markets[0].ref").isEqualTo(marketA.toString());
        assertThat(result).bodyJson().extractingPath("$.markets[0].name").isEqualTo("Mercado Exemplo A");
        assertThat(result).bodyJson().extractingPath("$.markets[0].total").isEqualTo("24.00");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.markets[0].missingCount")
                .isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.markets[1].total").isEqualTo("25.00");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.markets[1].missingCount")
                .isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.items.length()").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.items[0].quantity").isEqualTo("2");
        assertThat(result).bodyJson().extractingPath("$.items[0].common").isEqualTo(true);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].prices[0].unitPrice")
                .isEqualTo("4.5000");
        assertThat(result).bodyJson().extractingPath("$.items[1].unitMismatch").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.items[1].common").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.items[2].prices[1]").isNull();
    }

    @Test
    void oldAfterSixtyDays() {
        UUID milk = listed("Leite Exemplo 1 L", "1");
        UUID rice = listed("Arroz Exemplo 5 kg", "1");
        price(marketA, milk, "2026-09-25", "4.5000", "UN");
        price(marketA, rice, "2026-09-24", "20.0000", "UN");

        MvcTestResult result = compare(marketA + "," + marketB);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.items[0].prices[0].old").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.items[1].prices[0].old").isEqualTo(true);
    }

    @Test
    void marketsInRequestedOrder() {
        UUID milk = listed("Leite Exemplo 1 L", "1");
        price(marketA, milk, "2026-11-20", "4.0000", "UN");
        price(marketB, milk, "2026-11-20", "9.0000", "UN");

        MvcTestResult result = compare(marketB + "," + marketA);

        assertThat(result).bodyJson().extractingPath("$.markets[0].ref").isEqualTo(marketB.toString());
        assertThat(result).bodyJson().extractingPath("$.markets[0].total").isEqualTo("9.00");
        assertThat(result).bodyJson().extractingPath("$.markets[1].ref").isEqualTo(marketA.toString());
        assertThat(result).bodyJson().extractingPath("$.items[0].prices[0].ref").isEqualTo(marketB.toString());
    }

    @Test
    void suggestsTwoWithMostPrices() {
        UUID marketC = MarketsTestData.family(jdbc, ws, "Mercado Exemplo C", null, null);
        UUID[] products = new UUID[5];
        for (int i = 0; i < 5; i++) {
            products[i] = listed("Produto Exemplo " + i, "1");
            price(marketA, products[i], "2026-11-01", "1.0000", "UN");
        }
        for (int i = 0; i < 3; i++) {
            price(marketB, products[i], "2026-11-01", "1.0000", "UN");
        }
        price(marketC, products[0], "2026-11-20", "1.0000", "UN");

        MvcTestResult result = compare(null);

        assertThat(result).hasStatus(200);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.suggested")
                .isEqualTo(java.util.List.of(marketA.toString(), marketB.toString()));
        assertThat(result).bodyJson().extractingPath("$.markets.length()").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.items.length()").isEqualTo(5);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].prices.length()")
                .isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.commonCount").isEqualTo(0);
    }

    @Test
    void noSuggestionWithFewerThanTwoPricedMarkets() {
        UUID milk = listed("Leite Exemplo 1 L", "1");
        price(marketA, milk, "2026-11-01", "1.0000", "UN");

        assertThat(compare(null)).bodyJson().extractingPath("$.suggested").isEqualTo(java.util.List.of());
    }

    @Test
    void invalidMarkets() {
        UUID other = workspaces.createFor(users.create("Bia Teste", "bia@example.com"));
        UUID foreign = MarketsTestData.family(jdbc, other, "Mercado Alheio", null, null);
        UUID marketC = MarketsTestData.family(jdbc, ws, "Mercado Exemplo C", null, null);
        UUID marketD = MarketsTestData.family(jdbc, ws, "Mercado Exemplo D", null, null);

        for (String markets : new String[] {
            marketA.toString(),
            marketA + "," + marketB + "," + marketC + "," + marketD,
            marketA + "," + marketA,
            marketA + "," + marketB + "," + marketA
        }) {
            MvcTestResult result = compare(markets);
            assertThat(result).hasStatus(422);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("compare.markets_invalid");
        }
        MvcTestResult otherFamily = compare(marketA + "," + foreign);
        assertThat(otherFamily).hasStatus(404);
        assertThat(otherFamily).bodyJson().extractingPath("$.code").isEqualTo("market.not_found");
        assertThat(compare(marketA + ",not-a-ref")).hasStatus(404);
    }

    @Test
    void osmWithoutRowHasNoPrices() {
        UUID milk = listed("Leite Exemplo 1 L", "1");
        UUID rice = listed("Arroz Exemplo 5 kg", "1");
        price(marketA, milk, "2026-11-20", "4.0000", "UN");
        price(marketA, rice, "2026-11-20", "20.0000", "UN");

        MvcTestResult result = compare(marketA + ",osm-n-9000123");

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.markets[1].ref").isEqualTo("osm-n-9000123");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.markets[1].missingCount")
                .isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.markets[1].total").isEqualTo("0.00");
        assertThat(result).bodyJson().extractingPath("$.commonCount").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.items[0].prices[1]").isNull();
    }

    private MvcTestResult compare(String markets) {
        var request = mvc.get().uri("/v1/workspaces/" + ws + "/markets/compare").with(TestUsers.signedIn(ana, null));
        if (markets != null) {
            request.queryParam("markets", markets);
        }
        return request.exchange();
    }

    private UUID listed(String name, String quantity) {
        UUID product = jdbc.sql(
                        "insert into products (workspace_id, name, unit) values (:ws, :name, 'UN') returning id")
                .param("ws", ws)
                .param("name", name)
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        insert into shopping_list_items (workspace_id, product_id, quantity, position)
                        values (:ws, :product, cast(:quantity as numeric),
                                (select coalesce(max(position) + 1, 0) from shopping_list_items where workspace_id = :ws))""")
                .param("ws", ws)
                .param("product", product)
                .param("quantity", quantity)
                .update();
        return product;
    }

    private void price(UUID market, UUID product, String day, String price, String unit) {
        UUID receipt = jdbc.sql("""
                        insert into receipts (workspace_id, market_id, key_hash, issued_on, mode, item_count)
                        values (:ws, :market, :hash, cast(:day as date), 'MANUAL', 1) returning id""")
                .param("ws", ws)
                .param("market", market)
                .param("hash", UUID.randomUUID().toString())
                .param("day", day)
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        insert into price_records
                            (workspace_id, market_id, product_id, receipt_id, observed_on, unit, unit_price)
                        values (:ws, :market, :product, :receipt, cast(:day as date), :unit, cast(:price as numeric))""")
                .param("ws", ws)
                .param("market", market)
                .param("product", product)
                .param("receipt", receipt)
                .param("day", day)
                .param("unit", unit)
                .param("price", price)
                .update();
    }
}
