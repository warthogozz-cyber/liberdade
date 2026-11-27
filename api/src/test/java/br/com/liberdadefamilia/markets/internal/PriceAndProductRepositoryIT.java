package br.com.liberdadefamilia.markets.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.markets.domain.ProductUnit;
import br.com.liberdadefamilia.markets.internal.PriceRepository.LatestPrice;
import br.com.liberdadefamilia.markets.internal.ProductRepository.ProductRow;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The family's latest prices and product lookups: the newest wins, and a family sees only its own. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PriceAndProductRepositoryIT {

    @Autowired
    PriceRepository prices;

    @Autowired
    ProductRepository products;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    private UUID ws;
    private UUID other;
    private UUID actor;

    @BeforeEach
    void families() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        ws = workspaces.createFor(ana);
        other = workspaces.createFor(bia);
        actor = workspaces.membershipOf(ws, ana);
    }

    @Test
    void newestPriceWinsPerProductAndMarket() {
        UUID market = market(ws, "Mercado Um");
        UUID second = market(ws, "Mercado Dois");
        UUID rice = products.create(ws, "Arroz", ProductUnit.UN);
        UUID beans = products.create(ws, "Feijao", ProductUnit.KG);
        UUID receipt = receipt(ws, market, "k1");
        insert(ws, market, rice, receipt, "2026-11-01", "4.0000");
        insert(ws, market, rice, receipt, "2026-11-10", "5.5000");
        insert(ws, market, rice, receipt, "2026-11-05", "9.0000");
        insert(ws, market, beans, receipt, "2026-11-02", "7.2500");
        insert(ws, second, rice, receipt(ws, second, "k2"), "2026-11-12", "1.0000");

        List<LatestPrice> latest = prices.latestByMarket(ws, market, 10);

        assertThat(latest).extracting(LatestPrice::name).containsExactly("Arroz", "Feijao");
        assertThat(latest.get(0).unitPrice()).isEqualByComparingTo("5.5");
        assertThat(latest.get(0).observedOn()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(latest.get(1).unitPrice()).isEqualByComparingTo("7.25");
        assertThat(prices.latestByMarket(ws, second, 10)).hasSize(1);
        assertThat(prices.latestByMarket(ws, market, 1)).hasSize(1);
    }

    @Test
    void latestPerProductAndMarket() {
        UUID market = market(ws, "Mercado Exemplo 1");
        UUID second = market(ws, "Mercado Exemplo 2");
        UUID milk = products.create(ws, "Leite Exemplo 1 L", ProductUnit.L);
        UUID receipt = receipt(ws, market, "k1");
        insert(ws, market, milk, receipt, "2026-11-01", "4.0000");
        insert(ws, market, milk, receipt, "2026-11-10", "5.5000");
        insert(ws, second, milk, receipt(ws, second, "k2"), "2026-11-03", "6.0000");
        UUID theirs = market(other, "Mercado Alheio");
        insert(
                other,
                theirs,
                products.create(other, "Leite Alheio", ProductUnit.L),
                receipt(other, theirs, "k3"),
                "2026-11-03",
                "1.0000");

        List<LatestPrice> latest = prices.latestPerProductAndMarket(ws);

        assertThat(latest).hasSize(2);
        assertThat(latest)
                .extracting(LatestPrice::marketName)
                .containsExactly("Mercado Exemplo 1", "Mercado Exemplo 2");
        assertThat(latest.get(0).unitPrice()).isEqualByComparingTo("5.5");
        assertThat(latest.get(0).observedOn()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(latest.get(0).marketRef()).isEqualTo(market.toString());
        assertThat(latest.get(1).unitPrice()).isEqualByComparingTo("6");
        assertThat(prices.latestPerProductAndMarket(other)).hasSize(1);
    }

    @Test
    void findByNormalizedName() {
        UUID milk = products.create(ws, "Leite Integral Éxemplo", ProductUnit.L);
        products.create(other, "Leite Integral Exemplo", ProductUnit.L);

        assertThat(products.findByNormalizedName(ws, "leite integral exemplo")).contains(milk);
        assertThat(products.findByNormalizedName(ws, "leite")).isEmpty();
        assertThat(products.findByNormalizedName(UUID.randomUUID(), "leite integral exemplo"))
                .isEmpty();
    }

    @Test
    void anotherFamilySeesNothing() {
        UUID market = market(ws, "Mercado Um");
        UUID rice = products.create(ws, "Arroz", ProductUnit.UN);
        insert(ws, market, rice, receipt(ws, market, "k1"), "2026-11-10", "5.5000");

        assertThat(prices.latestByMarket(other, market, 10)).isEmpty();
        assertThat(products.search(other, "", 10)).isEmpty();
    }

    @Test
    void searchIgnoresCaseAndAccentsAndFiltersByFamily() {
        products.create(ws, "Feijão Preto", ProductUnit.KG);
        products.create(ws, "Arroz", ProductUnit.UN);
        products.create(other, "Feijão do outro", ProductUnit.KG);

        assertThat(products.search(ws, "FEIJAO", 10))
                .extracting(ProductRow::name)
                .containsExactly("Feijão Preto");
        assertThat(products.search(ws, "", 10)).extracting(ProductRow::name).containsExactly("Arroz", "Feijão Preto");
        assertThat(products.search(ws, "", 1)).hasSize(1);
        assertThat(products.search(ws, "zzz", 10)).isEmpty();
    }

    @Test
    void byGtinIsPerFamily() {
        UUID rice = products.create(ws, "Arroz", ProductUnit.UN);
        jdbc.sql("update products set gtin = '7891234567895' where id = :id")
                .param("id", rice)
                .update();

        assertThat(products.byGtin(ws, "7891234567895")).contains(rice);
        assertThat(products.byGtin(other, "7891234567895")).isEmpty();
        assertThat(products.byGtin(ws, "7890000000000")).isEmpty();
    }

    private UUID market(UUID workspace, String name) {
        return jdbc.sql("""
                        insert into markets (workspace_id, origin, name, kind)
                        values (:ws, 'FAMILY', :name, 'GREENGROCER') returning id""")
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

    private void insert(UUID workspace, UUID market, UUID product, UUID receipt, String day, String price) {
        prices.insert(
                workspace, market, product, receipt, LocalDate.parse(day), "UN", null, new BigDecimal(price), actor);
    }
}
