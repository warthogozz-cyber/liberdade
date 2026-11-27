package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.List;
import java.util.Map;
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

/** Joining two products into one, without losing or duplicating what points at them. */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
class ProductMergeIT {

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
    private UUID market;
    private UUID kept;
    private UUID removed;

    @BeforeEach
    void family() {
        doReturn(false).when(plans).legalSetEnabled();
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'markets', true)")
                .param("ws", ws)
                .update();
        market = MarketsTestData.family(jdbc, ws, "Mercado Exemplo 1", null, null);
        kept = product(ws, "Leite Exemplo 1 L", null);
        removed = product(ws, "Leite Ex. 1L", null);
        events.clear();
    }

    @Test
    void pricesAndAliasesMove() {
        UUID receipt = receipt(ws, market, "h1");
        price(removed, receipt, "2026-11-01", "4.00");
        price(removed, receipt, "2026-11-02", "4.20");
        alias(market, "leite ex 1l", removed);

        MvcTestResult result = merge(kept, removed);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(kept.toString());
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Leite Exemplo 1 L");
        assertThat(result).bodyJson().extractingPath("$.prices.length()").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.prices[0].unitPrice").isEqualTo("4.2000");
        assertThat(count("select count(*) from price_records where product_id = ?", kept))
                .isEqualTo(2);
        assertThat(count("select count(*) from product_aliases where product_id = ?", kept))
                .isEqualTo(1);
        assertThat(count("select count(*) from products where id = ?", removed)).isZero();
        assertThat(count("select count(*) from price_records where product_id = ?", removed))
                .isZero();
    }

    @Test
    void aliasConflictKeepsFirst() {
        // The primary key (family, market, description) already allows one product per description at a market, so
        // two products never share an alias: the merge keeps A's own and moves B's, and cannot collide.
        alias(market, "leite", kept);
        alias(market, "leite ex 1l", removed);
        UUID other = MarketsTestData.family(jdbc, ws, "Mercado Exemplo 2", null, null);
        alias(other, "leite", removed);

        assertThat(merge(kept, removed)).hasStatus(200);
        assertThat(count("select count(*) from product_aliases where product_id = ?", kept))
                .isEqualTo(3);

        assertThat(jdbc.sql("select market_id, product_id from product_aliases where workspace_id = :ws order by 1")
                        .param("ws", ws)
                        .query((rs, n) -> Map.entry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)))
                        .list())
                .contains(Map.entry(other, kept));
    }

    @Test
    void listQuantitiesSummed() {
        listItem(kept, "2", 0, true);
        listItem(removed, "1.5", 1, true);

        assertThat(merge(kept, removed)).hasStatus(200);

        assertThat(jdbc.sql("select quantity from shopping_list_items where workspace_id = :ws")
                        .param("ws", ws)
                        .query(java.math.BigDecimal.class)
                        .list())
                .singleElement()
                .satisfies(quantity -> assertThat(quantity).isEqualByComparingTo("3.5"));
        assertThat(count("select count(*) from shopping_list_items where product_id = ?", kept))
                .isEqualTo(1);
        assertThat(count(
                        "select count(*) from shopping_list_items where checked_at is not null and product_id = ?",
                        kept))
                .isEqualTo(1);
    }

    @Test
    void listSumIsCheckedOnlyWhenBothWere() {
        listItem(kept, "2", 0, true);
        listItem(removed, "1", 1, false);

        assertThat(merge(kept, removed)).hasStatus(200);

        assertThat(count("select count(*) from shopping_list_items where checked_at is null and product_id = ?", kept))
                .isEqualTo(1);
    }

    @Test
    void listItemMovesWhenOnlyRemovedListed() {
        UUID third = product(ws, "Arroz Exemplo", null);
        listItem(third, "1", 0, false);
        listItem(removed, "2", 1, true);

        assertThat(merge(kept, removed)).hasStatus(200);

        assertThat(jdbc.sql("select product_id, position, quantity from shopping_list_items"
                                + " where workspace_id = :ws and product_id = :p")
                        .param("ws", ws)
                        .param("p", kept)
                        .query((rs, n) -> rs.getInt("position") + "/"
                                + rs.getBigDecimal("quantity").intValue())
                        .single())
                .isEqualTo("1/2");
        assertThat(count("select count(*) from shopping_list_items where product_id = ?", removed))
                .isZero();
    }

    @Test
    void gtinMovesToKept() {
        jdbc.sql("update products set gtin = '7891234567895' where id = :id")
                .param("id", removed)
                .update();

        assertThat(merge(kept, removed)).hasStatus(200);

        assertThat(jdbc.sql("select gtin from products where id = :id")
                        .param("id", kept)
                        .query(String.class)
                        .single())
                .isEqualTo("7891234567895");
    }

    @Test
    void keptGtinStaysWhenOnlyKeptHasOne() {
        jdbc.sql("update products set gtin = '7891234567895' where id = :id")
                .param("id", kept)
                .update();

        assertThat(merge(kept, removed)).hasStatus(200);

        assertThat(jdbc.sql("select gtin from products where id = :id")
                        .param("id", kept)
                        .query(String.class)
                        .single())
                .isEqualTo("7891234567895");
    }

    @Test
    void differentGtinsIs422() {
        jdbc.sql("update products set gtin = '7891234567895' where id = :id")
                .param("id", kept)
                .update();
        jdbc.sql("update products set gtin = '4006381333931' where id = :id")
                .param("id", removed)
                .update();
        price(removed, receipt(ws, market, "h1"), "2026-11-01", "4.00");
        listItem(removed, "1", 0, false);

        MvcTestResult result = merge(kept, removed);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("product.merge_gtin_conflict");
        assertThat(count("select count(*) from products where id = ?", removed)).isEqualTo(1);
        assertThat(count("select count(*) from price_records where product_id = ?", removed))
                .isEqualTo(1);
        assertThat(count("select count(*) from shopping_list_items where product_id = ?", removed))
                .isEqualTo(1);
        assertThat(events.stream(ProductsMerged.class)).isEmpty();
    }

    @Test
    void sameIdIs422() {
        MvcTestResult result = merge(kept, kept);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("product.merge_same");
        assertThat(count("select count(*) from products where id = ?", kept)).isEqualTo(1);
    }

    @Test
    void foreignOrMissingProductIs404() {
        UUID other = workspaces.createFor(users.create("Bia Teste", "bia@example.com"));
        UUID foreign = product(other, "Leite Alheio", null);

        for (MvcTestResult result :
                List.of(merge(kept, foreign), merge(foreign, kept), merge(kept, UUID.randomUUID()))) {
            assertThat(result).hasStatus(404);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("product.not_found");
        }
        assertThat(count("select count(*) from products where id = ?", kept)).isEqualTo(1);
        assertThat(count("select count(*) from products where id = ?", foreign)).isEqualTo(1);
    }

    @Test
    void dismissalsWithRemovedGone() {
        UUID third = product(ws, "Arroz Exemplo", null);
        UUID fourth = product(ws, "Feijao Exemplo", null);
        dismiss(removed, third);
        dismiss(kept, fourth);

        assertThat(merge(kept, removed)).hasStatus(200);

        assertThat(jdbc.sql("select count(*) from product_join_dismissals where workspace_id = :ws")
                        .param("ws", ws)
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
        assertThat(count("select count(*) from product_join_dismissals where product_a = ? or product_b = ?", kept))
                .isEqualTo(1);
    }

    @Test
    void eventPublished() {
        assertThat(merge(kept, removed)).hasStatus(200);

        assertThat(events.stream(ProductsMerged.class)).containsExactly(new ProductsMerged(ws, kept, removed, ana));
    }

    private MvcTestResult merge(UUID keep, UUID remove) {
        var session = TestUsers.signedIn(ana, null);
        return mvc.method(HttpMethod.POST)
                .uri("/v1/workspaces/" + ws + "/markets/products/" + keep + "/merge")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"removeId\":\"" + remove + "\"}")
                .exchange();
    }

    private long count(String sql, UUID id) {
        return jdbc.sql(sql.replace("?", ":p")).param("p", id).query(Long.class).single();
    }

    private UUID product(UUID workspace, String name, String gtin) {
        return jdbc.sql("insert into products (workspace_id, name, gtin) values (:ws, :name, :gtin) returning id")
                .param("ws", workspace)
                .param("name", name)
                .param("gtin", gtin)
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

    private void price(UUID product, UUID receipt, String day, String price) {
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

    private void alias(UUID marketId, String norm, UUID product) {
        jdbc.sql("""
                        insert into product_aliases (workspace_id, market_id, description_norm, product_id)
                        values (:ws, :market, :norm, :product)""")
                .param("ws", ws)
                .param("market", marketId)
                .param("norm", norm)
                .param("product", product)
                .update();
    }

    private void listItem(UUID product, String quantity, int position, boolean checked) {
        jdbc.sql("""
                        insert into shopping_list_items (workspace_id, product_id, quantity, position, checked_at)
                        values (:ws, :product, cast(:q as numeric), :pos, case when :checked then now() end)""")
                .param("ws", ws)
                .param("product", product)
                .param("q", quantity)
                .param("pos", position)
                .param("checked", checked)
                .update();
    }

    private void dismiss(UUID a, UUID b) {
        UUID low = a.compareTo(b) < 0 ? a : b;
        UUID high = low.equals(a) ? b : a;
        jdbc.sql("insert into product_join_dismissals (workspace_id, product_a, product_b) values (:ws, :a, :b)")
                .param("ws", ws)
                .param("a", low)
                .param("b", high)
                .update();
    }
}
