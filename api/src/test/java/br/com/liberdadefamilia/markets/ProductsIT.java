package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.support.CsrfSession;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The family's products with their last prices, and renaming a product or changing its unit. */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
class ProductsIT {

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
    void overviewLatestPerMarket() {
        UUID first = MarketsTestData.family(jdbc, ws, "Mercado Exemplo 1", null, null);
        UUID second = MarketsTestData.family(jdbc, ws, "Mercado Exemplo 2", null, null);
        UUID milk = product(ws, "Leite Exemplo 1 L");
        UUID receipt = receipt(ws, first, "h1");
        price(ws, first, milk, receipt, "2026-11-01", "4.00", "UN");
        price(ws, first, milk, receipt, "2026-11-10", "4.50", "UN");
        price(ws, second, milk, receipt(ws, second, "h2"), "2026-11-05", "5.00", "UN");
        product(ws, "Arroz Exemplo");

        MvcTestResult result = overview();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.truncated").isEqualTo(false);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.products[*].name")
                .isEqualTo(List.of("Arroz Exemplo", "Leite Exemplo 1 L"));
        assertThat(result)
                .bodyJson()
                .extractingPath("$.products[0].prices.length()")
                .isEqualTo(0);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.products[1].prices.length()")
                .isEqualTo(2);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.products[1].prices[0].marketName")
                .isEqualTo("Mercado Exemplo 1");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.products[1].prices[0].marketRef")
                .isEqualTo(first.toString());
        assertThat(result)
                .bodyJson()
                .extractingPath("$.products[1].prices[0].unitPrice")
                .isEqualTo("4.5000");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.products[1].prices[0].unit")
                .isEqualTo("UN");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.products[1].prices[0].observedOn")
                .isEqualTo("2026-11-10");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.products[1].prices[1].unitPrice")
                .isEqualTo("5.0000");
    }

    @Test
    void overviewHidesOtherFamily() {
        UUID other = workspaces.createFor(users.create("Bia Teste", "bia@example.com"));
        product(other, "Leite Alheio");
        product(ws, "Leite Exemplo 1 L");

        assertThat(overview()).bodyJson().extractingPath("$.products.length()").isEqualTo(1);
    }

    @Test
    void renameAndUnit() {
        UUID market = MarketsTestData.family(jdbc, ws, "Mercado Exemplo 1", null, null);
        UUID milk = product(ws, "Leite Exemplo 1 L");
        price(ws, market, milk, receipt(ws, market, "h1"), "2026-11-10", "4.50", "UN");

        MvcTestResult result = patch(milk, "{\"name\":\"  Leite Exemplo Integral 1 L \",\"unit\":\"L\"}");

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Leite Exemplo Integral 1 L");
        assertThat(result).bodyJson().extractingPath("$.unit").isEqualTo("L");
        assertThat(jdbc.sql("select name from products where id = :id")
                        .param("id", milk)
                        .query(String.class)
                        .single())
                .isEqualTo("Leite Exemplo Integral 1 L");
        assertThat(jdbc.sql("select unit from price_records where product_id = :id")
                        .param("id", milk)
                        .query(String.class)
                        .single())
                .isEqualTo("UN");
        assertThat(events.stream(ProductsChanged.class)).containsExactly(new ProductsChanged(ws));
    }

    @Test
    void onlyTheUnitChanges() {
        UUID milk = product(ws, "Leite Exemplo 1 L");

        assertThat(patch(milk, "{\"unit\":\"KG\"}")).hasStatus(200);

        assertThat(jdbc.sql("select name || '/' || unit from products where id = :id")
                        .param("id", milk)
                        .query(String.class)
                        .single())
                .isEqualTo("Leite Exemplo 1 L/KG");
    }

    @Test
    void blankNameIs422() {
        UUID milk = product(ws, "Leite Exemplo 1 L");

        for (String name : new String[] {"  ", "", "x".repeat(81)}) {
            MvcTestResult result = patch(milk, "{\"name\":\"" + name + "\"}");
            assertThat(result).hasStatus(422);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("product.name_invalid");
        }
        assertThat(events.stream(ProductsChanged.class)).isEmpty();
    }

    @Test
    void foreignProductIs404() {
        UUID other = workspaces.createFor(users.create("Bia Teste", "bia@example.com"));
        UUID foreign = product(other, "Leite Alheio");

        MvcTestResult result = patch(foreign, "{\"name\":\"Outro\"}");

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("product.not_found");
        assertThat(jdbc.sql("select name from products where id = :id")
                        .param("id", foreign)
                        .query(String.class)
                        .single())
                .isEqualTo("Leite Alheio");
    }

    private MvcTestResult overview() {
        return mvc.get()
                .uri("/v1/workspaces/" + ws + "/markets/products/overview")
                .with(TestUsers.signedIn(ana, null))
                .exchange();
    }

    private MvcTestResult patch(UUID product, String body) {
        var session = TestUsers.signedIn(ana, null);
        return mvc.method(HttpMethod.PATCH)
                .uri("/v1/workspaces/" + ws + "/markets/products/" + product)
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private UUID product(UUID workspace, String name) {
        return jdbc.sql("insert into products (workspace_id, name, unit) values (:ws, :name, 'UN') returning id")
                .param("ws", workspace)
                .param("name", name)
                .query(UUID.class)
                .single();
    }

    private UUID receipt(UUID workspace, UUID market, String hash) {
        return jdbc.sql("""
                        insert into receipts (workspace_id, market_id, key_hash, issued_on, mode, item_count)
                        values (:ws, :market, :hash, '2026-11-10', 'MANUAL', 1) returning id""")
                .param("ws", workspace)
                .param("market", market)
                .param("hash", hash)
                .query(UUID.class)
                .single();
    }

    private void price(UUID workspace, UUID market, UUID product, UUID receipt, String day, String price, String unit) {
        jdbc.sql("""
                        insert into price_records
                            (workspace_id, market_id, product_id, receipt_id, observed_on, unit, unit_price)
                        values (:ws, :market, :product, :receipt, cast(:day as date), :unit, cast(:price as numeric))""")
                .param("ws", workspace)
                .param("market", market)
                .param("product", product)
                .param("receipt", receipt)
                .param("day", day)
                .param("unit", unit)
                .param("price", price)
                .update();
    }
}
