package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/** The family's one shopping list: adding, ticking, ordering and clearing. */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
class ShoppingListIT {

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
    private CsrfSession session;

    @BeforeEach
    void family() {
        doReturn(false).when(plans).legalSetEnabled();
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'markets', true)")
                .param("ws", ws)
                .update();
        session = CsrfSession.start(mvc, TestUsers.signedIn(ana, null));
        events.clear();
    }

    @Test
    void addByProductAndByName() {
        UUID milk = product(ws, "Leite Exemplo 1 L");

        MvcTestResult byProduct = send(HttpMethod.POST, "", "{\"productId\":\"" + milk + "\"}");
        MvcTestResult byName = send(HttpMethod.POST, "", "{\"newProductName\":\"Café Exemplo\"}");

        assertThat(byProduct).hasStatus(201);
        assertThat(byProduct).bodyJson().extractingPath("$.productId").isEqualTo(milk.toString());
        assertThat(byProduct).bodyJson().extractingPath("$.quantity").isEqualTo("1");
        assertThat(byProduct).bodyJson().extractingPath("$.checked").isEqualTo(false);
        assertThat(byName).hasStatus(201);
        assertThat(byName).bodyJson().extractingPath("$.name").isEqualTo("Café Exemplo");
        assertThat(byName).bodyJson().extractingPath("$.unit").isEqualTo("UN");
        assertThat(byName).bodyJson().extractingPath("$.prices.length()").isEqualTo(0);
        assertThat(byName).bodyJson().extractingPath("$.position").isEqualTo(1);
        assertThat(names(list())).containsExactly("Leite Exemplo 1 L", "Café Exemplo");
    }

    @Test
    void newNameReusesSameNormalized() {
        UUID coffee = product(ws, "Café Exemplo");

        MvcTestResult result = send(HttpMethod.POST, "", "{\"newProductName\":\"cafe exemplo\"}");

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.productId").isEqualTo(coffee.toString());
        assertThat(count("select count(*) from products where workspace_id = ?", ws))
                .isEqualTo(1);
    }

    @Test
    void addingAgainSumsAndUnchecks() {
        UUID milk = product(ws, "Leite Exemplo 1 L");
        UUID item = item(send(HttpMethod.POST, "", "{\"productId\":\"" + milk + "\",\"quantity\":\"1\"}"));
        assertThat(send(HttpMethod.PATCH, "/" + item, "{\"checked\":true}")).hasStatus(200);

        MvcTestResult again = send(HttpMethod.POST, "", "{\"productId\":\"" + milk + "\",\"quantity\":\"2\"}");

        assertThat(again).hasStatus(200);
        assertThat(again).bodyJson().extractingPath("$.id").isEqualTo(item.toString());
        assertThat(again).bodyJson().extractingPath("$.quantity").isEqualTo("3");
        assertThat(again).bodyJson().extractingPath("$.checked").isEqualTo(false);
        assertThat(count("select count(*) from shopping_list_items where workspace_id = ?", ws))
                .isEqualTo(1);
    }

    @Test
    void concurrentAddsOneItem() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> calls = Collections.nCopies(
                    4,
                    () -> send(HttpMethod.POST, "", "{\"newProductName\":\"Café Exemplo\"}")
                            .getResponse()
                            .getStatus());
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : pool.invokeAll(calls)) {
                statuses.add(future.get());
            }
            assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
            assertThat(statuses.stream().filter(s -> s == 200)).hasSize(3);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("select count(*) from products where workspace_id = ?", ws))
                .isEqualTo(1);
        assertThat(count("select count(*) from shopping_list_items where workspace_id = ?", ws))
                .isEqualTo(1);
        assertThat(jdbc.sql("select quantity from shopping_list_items where workspace_id = :ws")
                        .param("ws", ws)
                        .query(java.math.BigDecimal.class)
                        .single())
                .isEqualByComparingTo("4");
    }

    @Test
    void summedQuantityPastTheColumnIs422() {
        UUID milk = product(ws, "Leite Exemplo 1 L");
        assertThat(send(HttpMethod.POST, "", "{\"productId\":\"" + milk + "\",\"quantity\":\"999999999999999\"}"))
                .hasStatus(201);

        MvcTestResult again = send(HttpMethod.POST, "", "{\"productId\":\"" + milk + "\",\"quantity\":\"1\"}");

        assertThat(again).hasStatus(422);
        assertThat(again).bodyJson().extractingPath("$.code").isEqualTo("list.quantity_too_large");
        assertThat(jdbc.sql("select quantity from shopping_list_items where workspace_id = :ws")
                        .param("ws", ws)
                        .query(java.math.BigDecimal.class)
                        .single())
                .isEqualByComparingTo("999999999999999");
    }

    @Test
    void checkAndUncheck() {
        UUID item = item(send(HttpMethod.POST, "", "{\"newProductName\":\"Café Exemplo\"}"));

        MvcTestResult checked = send(HttpMethod.PATCH, "/" + item, "{\"checked\":true}");

        assertThat(checked).hasStatus(200);
        assertThat(checked).bodyJson().extractingPath("$.checked").isEqualTo(true);
        assertThat(checkedAt(item)).isNotNull();

        MvcTestResult unchecked = send(HttpMethod.PATCH, "/" + item, "{\"checked\":false}");

        assertThat(unchecked).bodyJson().extractingPath("$.checked").isEqualTo(false);
        assertThat(checkedAt(item)).isNull();
    }

    @Test
    void changeQuantity() {
        UUID item = item(send(HttpMethod.POST, "", "{\"newProductName\":\"Café Exemplo\"}"));

        MvcTestResult result = send(HttpMethod.PATCH, "/" + item, "{\"quantity\":\"2.5\"}");

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.quantity").isEqualTo("2.5");
    }

    @Test
    void quantityInvalid() {
        UUID milk = product(ws, "Leite Exemplo 1 L");
        UUID item = item(send(HttpMethod.POST, "", "{\"productId\":\"" + milk + "\"}"));

        for (String quantity : List.of("0", "1,5", "1.00001", "-1", "")) {
            MvcTestResult add =
                    send(HttpMethod.POST, "", "{\"productId\":\"" + milk + "\",\"quantity\":\"" + quantity + "\"}");
            assertThat(add).hasStatus(422);
            assertThat(add).bodyJson().extractingPath("$.code").isEqualTo("list.quantity_invalid");
            MvcTestResult patch = send(HttpMethod.PATCH, "/" + item, "{\"quantity\":\"" + quantity + "\"}");
            assertThat(patch).hasStatus(422);
            assertThat(patch).bodyJson().extractingPath("$.code").isEqualTo("list.quantity_invalid");
        }
    }

    @Test
    void reorder() {
        UUID a = item(send(HttpMethod.POST, "", "{\"newProductName\":\"Arroz Exemplo\"}"));
        UUID b = item(send(HttpMethod.POST, "", "{\"newProductName\":\"Feijão Exemplo\"}"));
        UUID c = item(send(HttpMethod.POST, "", "{\"newProductName\":\"Café Exemplo\"}"));

        MvcTestResult result = send(HttpMethod.PUT, "/order", "{\"ids\":[\"" + c + "\",\"" + a + "\",\"" + b + "\"]}");

        assertThat(result).hasStatus(200);
        assertThat(names(list())).containsExactly("Café Exemplo", "Arroz Exemplo", "Feijão Exemplo");
        assertThat(result).bodyJson().extractingPath("$.items[0].id").isEqualTo(c.toString());
    }

    @Test
    void staleOrderIs409() {
        UUID a = item(send(HttpMethod.POST, "", "{\"newProductName\":\"Arroz Exemplo\"}"));
        send(HttpMethod.POST, "", "{\"newProductName\":\"Feijão Exemplo\"}");

        MvcTestResult missing = send(HttpMethod.PUT, "/order", "{\"ids\":[\"" + a + "\"]}");
        MvcTestResult unknown =
                send(HttpMethod.PUT, "/order", "{\"ids\":[\"" + a + "\",\"" + UUID.randomUUID() + "\"]}");
        MvcTestResult repeated = send(HttpMethod.PUT, "/order", "{\"ids\":[\"" + a + "\",\"" + a + "\"]}");

        for (MvcTestResult result : List.of(missing, unknown, repeated)) {
            assertThat(result).hasStatus(409);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("list.order_stale");
        }
    }

    @Test
    void clearChecked() {
        UUID a = item(send(HttpMethod.POST, "", "{\"newProductName\":\"Arroz Exemplo\"}"));
        UUID b = item(send(HttpMethod.POST, "", "{\"newProductName\":\"Feijão Exemplo\"}"));
        send(HttpMethod.POST, "", "{\"newProductName\":\"Café Exemplo\"}");
        send(HttpMethod.PATCH, "/" + a, "{\"checked\":true}");
        send(HttpMethod.PATCH, "/" + b, "{\"checked\":true}");

        MvcTestResult result = send(HttpMethod.POST, "/clear-checked", null);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.removed").isEqualTo(2);
        assertThat(names(list())).containsExactly("Café Exemplo");
    }

    @Test
    void removeItem() {
        UUID item = item(send(HttpMethod.POST, "", "{\"newProductName\":\"Café Exemplo\"}"));

        assertThat(send(HttpMethod.DELETE, "/" + item, null)).hasStatus(204);

        assertThat(names(list())).isEmpty();
        assertThat(count("select count(*) from products where workspace_id = ?", ws))
                .isEqualTo(1);
    }

    @Test
    void otherFamilyItemIs404() {
        UUID other = workspaces.createFor(users.create("Bia Teste", "bia@example.com"));
        UUID foreign = jdbc.sql("insert into products (workspace_id, name) values (:ws, 'Leite Alheio') returning id")
                .param("ws", other)
                .query(UUID.class)
                .single();
        UUID foreignItem = jdbc.sql("""
                        insert into shopping_list_items (workspace_id, product_id, position)
                        values (:ws, :product, 0) returning id""")
                .param("ws", other)
                .param("product", foreign)
                .query(UUID.class)
                .single();

        for (MvcTestResult result : List.of(
                send(HttpMethod.PATCH, "/" + foreignItem, "{\"checked\":true}"),
                send(HttpMethod.DELETE, "/" + foreignItem, null))) {
            assertThat(result).hasStatus(404);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("list.item_not_found");
        }
        MvcTestResult otherProduct = send(HttpMethod.POST, "", "{\"productId\":\"" + foreign + "\"}");
        assertThat(otherProduct).hasStatus(404);
        assertThat(otherProduct).bodyJson().extractingPath("$.code").isEqualTo("product.not_found");
        assertThat(names(list())).isEmpty();
    }

    @Test
    void addNeedsExactlyOneProduct() {
        MvcTestResult neither = send(HttpMethod.POST, "", "{}");
        MvcTestResult both =
                send(HttpMethod.POST, "", "{\"productId\":\"" + UUID.randomUUID() + "\",\"newProductName\":\"Café\"}");
        MvcTestResult blank = send(HttpMethod.POST, "", "{\"newProductName\":\"   \"}");

        assertThat(neither).hasStatus(422);
        assertThat(neither).bodyJson().extractingPath("$.code").isEqualTo("list.product_required");
        assertThat(both).hasStatus(422);
        assertThat(both).bodyJson().extractingPath("$.code").isEqualTo("list.product_required");
        assertThat(blank).hasStatus(422);
        assertThat(blank).bodyJson().extractingPath("$.code").isEqualTo("product.name_invalid");
    }

    @Test
    void listFull() {
        jdbc.sql("""
                        insert into products (workspace_id, name)
                        select :ws, 'Produto Exemplo ' || n from generate_series(1, 301) n""").param("ws", ws).update();
        jdbc.sql("""
                        insert into shopping_list_items (workspace_id, product_id, position)
                        select :ws, id, row_number() over (order by id) from products
                        where workspace_id = :ws and name <> 'Produto Exemplo 301'""").param("ws", ws).update();
        UUID last = jdbc.sql("select id from products where workspace_id = :ws and name = 'Produto Exemplo 301'")
                .param("ws", ws)
                .query(UUID.class)
                .single();
        // 300 items: the 301st does not fit, but one already on the list can still be added to.
        UUID existing = jdbc.sql("select product_id from shopping_list_items where workspace_id = :ws limit 1")
                .param("ws", ws)
                .query(UUID.class)
                .single();

        MvcTestResult full = send(HttpMethod.POST, "", "{\"productId\":\"" + last + "\"}");
        MvcTestResult sum = send(HttpMethod.POST, "", "{\"productId\":\"" + existing + "\"}");
        MvcTestResult named = send(HttpMethod.POST, "", "{\"newProductName\":\"Produto Novo Exemplo\"}");

        assertThat(full).hasStatus(422);
        assertThat(full).bodyJson().extractingPath("$.code").isEqualTo("list.full");
        assertThat(sum).hasStatus(200);
        assertThat(named).hasStatus(422);
        assertThat(count("select count(*) from shopping_list_items where workspace_id = ?", ws))
                .isEqualTo(300);
        assertThat(count("select count(*) from products where workspace_id = ?", ws))
                .isEqualTo(301);
    }

    @Test
    void pricesPerMarket() {
        UUID first = MarketsTestData.family(jdbc, ws, "Mercado Exemplo 1", null, null);
        UUID second = MarketsTestData.family(jdbc, ws, "Mercado Exemplo 2", null, null);
        UUID milk = product(ws, "Leite Exemplo 1 L");
        price(first, milk, "h1", "2026-11-10", "4.50");
        price(second, milk, "h2", "2026-11-05", "5.00");
        send(HttpMethod.POST, "", "{\"productId\":\"" + milk + "\"}");

        MvcTestResult result = list();

        assertThat(result).hasStatus(200);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].prices.length()")
                .isEqualTo(2);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].prices[0].marketName")
                .isEqualTo("Mercado Exemplo 1");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].prices[0].unitPrice")
                .isEqualTo("4.5000");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].prices[1].unitPrice")
                .isEqualTo("5.0000");
    }

    @Test
    void listHidesOtherFamily() {
        UUID other = workspaces.createFor(users.create("Bia Teste", "bia@example.com"));
        UUID foreign = product(other, "Leite Alheio");
        jdbc.sql("insert into shopping_list_items (workspace_id, product_id, position) values (:ws, :p, 0)")
                .param("ws", other)
                .param("p", foreign)
                .update();
        send(HttpMethod.POST, "", "{\"newProductName\":\"Café Exemplo\"}");

        assertThat(names(list())).containsExactly("Café Exemplo");
    }

    @Test
    void eventOnEveryWrite() {
        UUID item = item(send(HttpMethod.POST, "", "{\"newProductName\":\"Arroz Exemplo\"}"));
        UUID other = item(send(HttpMethod.POST, "", "{\"newProductName\":\"Café Exemplo\"}"));
        List<MvcTestResult> writes = new ArrayList<>();
        writes.add(send(HttpMethod.PATCH, "/" + item, "{\"checked\":true}"));
        writes.add(send(HttpMethod.PUT, "/order", "{\"ids\":[\"" + other + "\",\"" + item + "\"]}"));
        writes.add(send(HttpMethod.POST, "/clear-checked", null));
        writes.add(send(HttpMethod.DELETE, "/" + other, null));

        assertThat(writes)
                .allSatisfy(
                        result -> assertThat(result.getResponse().getStatus()).isBetween(200, 299));
        // Two adds, then one event for each of the four writes.
        assertThat(events.stream(ShoppingListChanged.class))
                .containsExactlyElementsOf(Collections.nCopies(6, new ShoppingListChanged(ws)));
    }

    @Test
    void readsPublishNothing() {
        send(HttpMethod.POST, "", "{\"newProductName\":\"Café Exemplo\"}");
        events.clear();

        list();

        assertThat(events.stream(ShoppingListChanged.class)).isEmpty();
    }

    private MvcTestResult list() {
        return mvc.get()
                .uri("/v1/workspaces/" + ws + "/markets/list")
                .with(TestUsers.signedIn(ana, null))
                .exchange();
    }

    private MvcTestResult send(HttpMethod method, String suffix, String body) {
        var request = mvc.method(method)
                .uri("/v1/workspaces/" + ws + "/markets/list" + suffix)
                .with(TestUsers.signedIn(ana, null))
                .with(session)
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(body);
        }
        return request.exchange();
    }

    private List<String> names(MvcTestResult list) {
        assertThat(list).hasStatus(200);
        return com.jayway.jsonpath.JsonPath.read(bodyOf(list), "$.items[*].name");
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static UUID item(MvcTestResult added) {
        assertThat(added.getResponse().getStatus()).isBetween(200, 299);
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(bodyOf(added), "$.id"));
    }

    private Object checkedAt(UUID item) {
        return jdbc.sql("select checked_at from shopping_list_items where id = :id")
                .param("id", item)
                .query((rs, n) -> Optional.ofNullable(rs.getTimestamp("checked_at")))
                .single()
                .orElse(null);
    }

    private int count(String sql, UUID id) {
        return jdbc.sql(sql.replace("?", ":id"))
                .param("id", id)
                .query(Integer.class)
                .single();
    }

    private UUID product(UUID workspace, String name) {
        return jdbc.sql("insert into products (workspace_id, name, unit) values (:ws, :name, 'UN') returning id")
                .param("ws", workspace)
                .param("name", name)
                .query(UUID.class)
                .single();
    }

    private void price(UUID market, UUID product, String hash, String day, String price) {
        UUID receipt = jdbc.sql("""
                        insert into receipts (workspace_id, market_id, key_hash, issued_on, mode, item_count)
                        values (:ws, :market, :hash, cast(:day as date), 'MANUAL', 1) returning id""")
                .param("ws", ws)
                .param("market", market)
                .param("hash", hash)
                .param("day", day)
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        insert into price_records
                            (workspace_id, market_id, product_id, receipt_id, observed_on, unit, unit_price)
                        values (:ws, :market, :product, :receipt, cast(:day as date), 'UN', cast(:price as numeric))""")
                .param("ws", ws)
                .param("market", market)
                .param("product", product)
                .param("receipt", receipt)
                .param("day", day)
                .param("price", price)
                .update();
    }
}
