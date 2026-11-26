package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The V41 schema: one receipt per key hash and family, positive prices, one GTIN per family, and the cascades. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReceiptsSchemaIT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    private UUID workspaceId;
    private UUID marketId;

    @BeforeEach
    void family() {
        workspaceId = workspaces.createFor(users.create("Ana Teste", "ana@example.com"));
        marketId = MarketsTestData.family(jdbc, workspaceId, "Mercado Exemplo", null, null);
    }

    @Test
    void keyHashOncePerFamily() {
        receipt(workspaceId, marketId, "hash-1");

        assertThatThrownBy(() -> receipt(workspaceId, marketId, "hash-1"))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID other = workspaces.createFor(users.create("Bia Teste", "bia@example.com"));
        UUID otherMarket = MarketsTestData.family(jdbc, other, "Mercado Exemplo", null, null);
        assertThatCode(() -> receipt(other, otherMarket, "hash-1")).doesNotThrowAnyException();
    }

    @Test
    void priceMustBePositive() {
        UUID product = product(workspaceId, "Leite", null);

        assertThatThrownBy(() -> price(workspaceId, marketId, product, "0", "1", "UN"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> price(workspaceId, marketId, product, "-1", "1", "UN"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> price(workspaceId, marketId, product, "4.99", "0", "UN"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> price(workspaceId, marketId, product, "4.99", "1", "XX"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> price(workspaceId, marketId, product, "4.99", "1.5", "KG"))
                .doesNotThrowAnyException();
        assertThatCode(() -> price(workspaceId, marketId, product, "4.99", null, "UN"))
                .doesNotThrowAnyException();
    }

    @Test
    void gtinOncePerFamily() {
        product(workspaceId, "Leite", "4006381333931");

        assertThatThrownBy(() -> product(workspaceId, "Outro leite", "4006381333931"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> product(workspaceId, "Sem código", null)).doesNotThrowAnyException();
        assertThatCode(() -> product(workspaceId, "Sem código 2", null)).doesNotThrowAnyException();

        UUID other = workspaces.createFor(users.create("Bia Teste", "bia@example.com"));
        assertThatCode(() -> product(other, "Leite", "4006381333931")).doesNotThrowAnyException();
    }

    @Test
    void productChecks() {
        assertThatThrownBy(() -> product(workspaceId, "", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> product(workspaceId, "x".repeat(81), null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> product(workspaceId, "x".repeat(80), null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> jdbc.sql("insert into products (workspace_id, name, unit) values (:ws, 'Leite', 'XX')")
                        .param("ws", workspaceId)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void itemCountIsBetween1And200() {
        assertThatThrownBy(() -> receipt(workspaceId, marketId, "h0", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> receipt(workspaceId, marketId, "h201", 201))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> receipt(workspaceId, marketId, "h200", 200)).doesNotThrowAnyException();
    }

    @Test
    void cascadeWithMarket() {
        UUID product = product(workspaceId, "Leite", null);
        UUID receipt = receipt(workspaceId, marketId, "hash-1");
        jdbc.sql(
                        "insert into product_aliases (workspace_id, market_id, description_norm, product_id) values (:ws, :m, 'leite', :p)")
                .param("ws", workspaceId)
                .param("m", marketId)
                .param("p", product)
                .update();
        jdbc.sql("""
                        insert into price_records (workspace_id, market_id, product_id, receipt_id, observed_on, unit, unit_price)
                        values (:ws, :m, :p, :r, current_date, 'UN', 4.99)""")
                .param("ws", workspaceId)
                .param("m", marketId)
                .param("p", product)
                .param("r", receipt)
                .update();

        jdbc.sql("delete from markets where id = :m").param("m", marketId).update();

        for (String table : new String[] {"product_aliases", "receipts", "price_records"}) {
            assertThat(count(table)).as(table).isZero();
        }
        assertThat(count("products")).as("products stay").isEqualTo(1);
    }

    private long count(String table) {
        return jdbc.sql("select count(*) from " + table + " where workspace_id = :ws")
                .param("ws", workspaceId)
                .query(Long.class)
                .single();
    }

    private UUID receipt(UUID ws, UUID market, String hash) {
        return receipt(ws, market, hash, 1);
    }

    private UUID receipt(UUID ws, UUID market, String hash, int items) {
        return jdbc.sql("""
                        insert into receipts (workspace_id, market_id, key_hash, issued_on, mode, item_count)
                        values (:ws, :m, :h, current_date, 'MANUAL', :n) returning id""")
                .param("ws", ws)
                .param("m", market)
                .param("h", hash)
                .param("n", items)
                .query(UUID.class)
                .single();
    }

    private UUID product(UUID ws, String name, String gtin) {
        return jdbc.sql("insert into products (workspace_id, name, gtin) values (:ws, :name, :gtin) returning id")
                .param("ws", ws)
                .param("name", name)
                .param("gtin", gtin)
                .query(UUID.class)
                .single();
    }

    private void price(UUID ws, UUID market, UUID product, String price, String quantity, String unit) {
        jdbc.sql("""
                        insert into price_records (workspace_id, market_id, product_id, observed_on, unit, quantity, unit_price)
                        values (:ws, :m, :p, current_date, :unit, cast(:q as numeric), cast(:price as numeric))""")
                .param("ws", ws)
                .param("m", market)
                .param("p", product)
                .param("unit", unit)
                .param("q", quantity)
                .param("price", price)
                .update();
    }
}
