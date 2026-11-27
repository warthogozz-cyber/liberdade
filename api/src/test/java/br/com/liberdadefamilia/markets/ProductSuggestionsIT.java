package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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

/** Pairs of products that may be the same one, and the pairs the family refused. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductSuggestionsIT {

    private static final Random RANDOM = new Random();

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
    void similarPairSuggested() {
        UUID first = product(ws, "Leite Exemplo 1L", null);
        UUID second = product(ws, "Leite Exemplo Integral 1L", null);
        product(ws, "Arroz Exemplo", null);
        UUID a = smaller(first, second);
        UUID b = a.equals(first) ? second : first;

        MvcTestResult result = suggestions();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.pairs.length()").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.pairs[0].a.id").isEqualTo(a.toString());
        assertThat(result).bodyJson().extractingPath("$.pairs[0].b.id").isEqualTo(b.toString());
        assertThat(result).bodyJson().extractingPath("$.pairs[0].score").isEqualTo(0.75);
        assertThat(result).bodyJson().extractingPath("$.pairs[0].a.unit").isEqualTo("UN");
    }

    @Test
    void aIsTheSmallerIdInDatabaseOrder() {
        UUID high = product(ws, topBitSet(), "Leite Exemplo 1L");
        UUID low = product(ws, topBitClear(), "Leite Exemplo Integral 1L");

        MvcTestResult result = suggestions();

        assertThat(result).bodyJson().extractingPath("$.pairs[0].a.id").isEqualTo(low.toString());
        assertThat(result).bodyJson().extractingPath("$.pairs[0].b.id").isEqualTo(high.toString());
    }

    @Test
    void dismissWithTopBitSetIdsKeepsDatabaseOrder() {
        UUID high = product(ws, topBitSet(), "Leite Exemplo 1L");
        UUID low = product(ws, topBitClear(), "Leite Exemplo Integral 1L");

        assertThat(dismiss(high, low)).hasStatus(204);

        assertThat(jdbc.sql("select product_a from product_join_dismissals where workspace_id = :ws")
                        .param("ws", ws)
                        .query(UUID.class)
                        .single())
                .isEqualTo(low);
        assertThat(suggestions()).bodyJson().extractingPath("$.pairs.length()").isEqualTo(0);
    }

    @Test
    void differentGtinsNotSuggested() {
        product(ws, "Leite Exemplo 1L", "7891000100103");
        product(ws, "Leite Exemplo Integral 1L", "7891000315507");

        assertThat(suggestions()).bodyJson().extractingPath("$.pairs.length()").isEqualTo(0);
    }

    @Test
    void oneGtinStillSuggested() {
        product(ws, "Leite Exemplo 1L", "7891000100103");
        product(ws, "Leite Exemplo Integral 1L", null);

        assertThat(suggestions()).bodyJson().extractingPath("$.pairs.length()").isEqualTo(1);
    }

    @Test
    void dismissedNotSuggested() {
        UUID first = product(ws, "Leite Exemplo 1L", null);
        UUID second = product(ws, "Leite Exemplo Integral 1L", null);
        UUID small = smaller(first, second);
        UUID big = small.equals(first) ? second : first;

        assertThat(dismiss(big, small)).hasStatus(204);
        assertThat(dismiss(big, small)).hasStatus(204);

        assertThat(suggestions()).bodyJson().extractingPath("$.pairs.length()").isEqualTo(0);
        assertThat(jdbc.sql("select count(*) from product_join_dismissals where workspace_id = :ws")
                        .param("ws", ws)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void belowThresholdNotSuggested() {
        product(ws, "Leite Exemplo", null);
        product(ws, "Leite Teste Integral Desnatado", null);

        assertThat(suggestions()).bodyJson().extractingPath("$.pairs.length()").isEqualTo(0);
    }

    @Test
    void bestScoreFirst() {
        product(ws, "Leite Exemplo Integral 1L", null);
        product(ws, "Leite Exemplo Integral", null);
        product(ws, "Biscoito Recheado Exemplo", null);
        product(ws, "Biscoito Recheado Exemplo Morango", null);

        MvcTestResult result = suggestions();

        assertThat(result).bodyJson().extractingPath("$.pairs.length()").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.pairs[0].score").isEqualTo(0.75);
    }

    @Test
    void equalScoresComeInNameOrder() {
        // Every pair shares two of four words, so every score is 0.5; the order must not depend on insertion.
        for (String suffix : new String[] {"Eta", "Alfa", "Delta", "Beta", "Gama"}) {
            product(ws, "Leite Exemplo " + suffix, null);
        }

        MvcTestResult result = suggestions();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.pairs.length()").isEqualTo(10);
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            keys.add(pairName(result, i, "a") + "|" + pairName(result, i, "b"));
        }
        assertThat(keys).isSorted();
    }

    private static String pairName(MvcTestResult result, int index, String side) {
        return com.jayway.jsonpath.JsonPath.read(bodyOf(result), "$.pairs[" + index + "]." + side + ".name");
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void foreignProductIs404() {
        UUID mine = product(ws, "Leite Exemplo 1L", null);
        UUID other = workspaces.createFor(users.create("Bia Teste", "bia@example.com"));
        UUID foreign = product(other, "Leite Alheio", null);

        MvcTestResult result = dismiss(mine, foreign);

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("product.not_found");
    }

    @Test
    void sameProductIs422() {
        UUID mine = product(ws, "Leite Exemplo 1L", null);

        assertThat(dismiss(mine, mine)).hasStatus(422);
    }

    @Test
    void hidesOtherFamily() {
        UUID other = workspaces.createFor(users.create("Bia Teste", "bia@example.com"));
        product(other, "Leite Exemplo 1L", null);
        product(other, "Leite Exemplo Integral 1L", null);

        assertThat(suggestions()).bodyJson().extractingPath("$.pairs.length()").isEqualTo(0);
    }

    /** Signed, Java sees it as smaller than any id with the top bit clear; Postgres sees it as larger. */
    private static UUID topBitSet() {
        return new UUID(RANDOM.nextLong() | Long.MIN_VALUE, RANDOM.nextLong());
    }

    private static UUID topBitClear() {
        return new UUID(RANDOM.nextLong() & Long.MAX_VALUE, RANDOM.nextLong());
    }

    /** The smaller id as Postgres orders uuids, which is also how the pair is stored. */
    private UUID smaller(UUID x, UUID y) {
        return jdbc.sql("select least(cast(:x as uuid), cast(:y as uuid))")
                .param("x", x)
                .param("y", y)
                .query(UUID.class)
                .single();
    }

    private MvcTestResult suggestions() {
        return mvc.get()
                .uri("/v1/workspaces/" + ws + "/markets/products/suggestions")
                .with(TestUsers.signedIn(ana, null))
                .exchange();
    }

    private MvcTestResult dismiss(UUID a, UUID b) {
        var session = TestUsers.signedIn(ana, null);
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/markets/products/dismissals")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"a\":\"" + a + "\",\"b\":\"" + b + "\"}")
                .exchange();
    }

    private UUID product(UUID workspace, String name, String gtin) {
        return jdbc.sql("""
                        insert into products (workspace_id, name, unit, gtin)
                        values (:ws, :name, 'UN', :gtin) returning id""")
                .param("ws", workspace)
                .param("name", name)
                .param("gtin", gtin)
                .query(UUID.class)
                .single();
    }

    private UUID product(UUID workspace, UUID id, String name) {
        return jdbc.sql("""
                        insert into products (id, workspace_id, name, unit)
                        values (:id, :ws, :name, 'UN') returning id""")
                .param("id", id)
                .param("ws", workspace)
                .param("name", name)
                .query(UUID.class)
                .single();
    }
}
