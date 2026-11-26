package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The last prices on the market card and the family's products. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MarketPricesIT {

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
    private UUID receipt;

    @BeforeEach
    void family() {
        doReturn(false).when(plans).legalSetEnabled();
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'markets', true)")
                .param("ws", ws)
                .update();
        market = MarketsTestData.family(jdbc, ws, "Feira Exemplo", null, null);
        receipt = receipt(ws, market, "h1");
    }

    @Test
    void latestPerProduct() {
        UUID rice = product(ws, "Arroz Exemplo");
        price(ws, market, rice, receipt, "2026-11-10", "4.00");
        price(ws, market, rice, receipt, "2026-11-20", "5.00");

        MvcTestResult result = prices(market.toString());

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.prices.length()").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.prices[0].unitPrice").isEqualTo("5.0000");
        assertThat(result).bodyJson().extractingPath("$.prices[0].observedOn").isEqualTo("2026-11-20");
        assertThat(result).bodyJson().extractingPath("$.prices[0].productId").isEqualTo(rice.toString());
        assertThat(result).bodyJson().extractingPath("$.prices[0].name").isEqualTo("Arroz Exemplo");
        assertThat(result).bodyJson().extractingPath("$.prices[0].unit").isEqualTo("UN");
    }

    @Test
    void sameDayLatestCreatedWins() {
        UUID rice = product(ws, "Arroz Exemplo");
        price(ws, market, rice, receipt, "2026-11-20", "4.00");
        price(ws, market, rice, receipt, "2026-11-20", "6.00");

        assertThat(prices(market.toString()))
                .bodyJson()
                .extractingPath("$.prices[0].unitPrice")
                .isEqualTo("6.0000");
    }

    @Test
    void fourDecimalsAsText() {
        price(ws, market, product(ws, "Arroz Exemplo"), receipt, "2026-11-20", "4.99");

        assertThat(prices(market.toString()))
                .bodyJson()
                .extractingPath("$.prices[0].unitPrice")
                .isEqualTo("4.9900");
    }

    @Test
    void pricesComeInNameOrder() {
        price(ws, market, product(ws, "Leite Exemplo"), receipt, "2026-11-20", "5.00");
        price(ws, market, product(ws, "Arroz Exemplo"), receipt, "2026-11-20", "4.00");

        assertThat(prices(market.toString()))
                .bodyJson()
                .extractingPath("$.prices[0].name")
                .isEqualTo("Arroz Exemplo");
    }

    @Test
    void osmWithoutRowIsEmpty() {
        MvcTestResult result = prices("osm-n-1");

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.prices.length()").isEqualTo(0);
    }

    @Test
    void unknownRefIs404() {
        MvcTestResult result = prices(UUID.randomUUID().toString());

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("market.not_found");
    }

    @Test
    void garbageRefIs404() {
        assertThat(prices("nao-e-ref")).hasStatus(404);
    }

    @Test
    void productsSortedAndFiltered() {
        product(ws, "Leite Integral Exemplo");
        product(ws, "Arroz Exemplo");
        product(ws, "Leite Exemplo");

        assertThat(products(""))
                .bodyJson()
                .extractingPath("$.products[*].name")
                .isEqualTo(List.of("Arroz Exemplo", "Leite Exemplo", "Leite Integral Exemplo"));
        assertThat(products("?q=leite"))
                .bodyJson()
                .extractingPath("$.products[*].name")
                .isEqualTo(List.of("Leite Exemplo", "Leite Integral Exemplo"));
        assertThat(products("?q=LÉITE"))
                .bodyJson()
                .extractingPath("$.products[*].name")
                .isEqualTo(List.of("Leite Exemplo", "Leite Integral Exemplo"));
    }

    @Test
    void productFieldsAndOtherFamilyHidden() {
        UUID other = workspaces.createFor(users.create("Bia Teste", "bia@example.com"));
        product(other, "Leite Alheio");
        product(ws, "Leite Exemplo");

        MvcTestResult result = products("?q=leite");

        assertThat(result).bodyJson().extractingPath("$.products.length()").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.products[0].unit").isEqualTo("UN");
        assertThat(result).bodyJson().extractingPath("$.products[0].gtin").isNull();
    }

    @Test
    void longQueryIs422() {
        assertThat(products("?q=" + "a".repeat(81))).hasStatus(422);
    }

    @Test
    void noPricesFromOtherFamily() {
        UUID other = workspaces.createFor(users.create("Bia Teste", "bia@example.com"));
        UUID mine = MarketsTestData.familyRowForOsm(jdbc, ws, 1);
        UUID theirs = MarketsTestData.familyRowForOsm(jdbc, other, 1);
        price(ws, mine, product(ws, "Arroz Exemplo"), receipt(ws, mine, "h2"), "2026-11-20", "4.00");
        price(other, theirs, product(other, "Feijao Alheio"), receipt(other, theirs, "h3"), "2026-11-20", "9.00");

        MvcTestResult result = prices("osm-n-1");

        assertThat(result).bodyJson().extractingPath("$.prices.length()").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.prices[0].name").isEqualTo("Arroz Exemplo");
    }

    private MvcTestResult prices(String ref) {
        return mvc.get()
                .uri("/v1/workspaces/" + ws + "/markets/" + ref + "/prices")
                .with(TestUsers.signedIn(ana, null))
                .exchange();
    }

    private MvcTestResult products(String query) {
        return mvc.get()
                .uri("/v1/workspaces/" + ws + "/markets/products" + query)
                .with(TestUsers.signedIn(ana, null))
                .exchange();
    }

    private UUID product(UUID workspace, String name) {
        return jdbc.sql("insert into products (workspace_id, name, unit) values (:ws, :name, 'UN') returning id")
                .param("ws", workspace)
                .param("name", name)
                .query(UUID.class)
                .single();
    }

    private UUID receipt(UUID workspace, UUID marketId, String hash) {
        return jdbc.sql("""
                        insert into receipts (workspace_id, market_id, key_hash, issued_on, mode, item_count)
                        values (:ws, :market, :hash, '2026-11-10', 'MANUAL', 1) returning id""")
                .param("ws", workspace)
                .param("market", marketId)
                .param("hash", hash)
                .query(UUID.class)
                .single();
    }

    private void price(UUID workspace, UUID marketId, UUID product, UUID receiptId, String day, String unitPrice) {
        jdbc.sql("""
                        insert into price_records
                            (workspace_id, market_id, product_id, receipt_id, observed_on, unit, unit_price)
                        values (:ws, :market, :product, :receipt, cast(:day as date), 'UN', cast(:price as numeric))""")
                .param("ws", workspace)
                .param("market", marketId)
                .param("product", product)
                .param("receipt", receiptId)
                .param("day", day)
                .param("price", unitPrice)
                .update();
    }
}
