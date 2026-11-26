package br.com.liberdadefamilia.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.markets.domain.NfceKeyFixtures;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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

/** Receipts, products and prices in the family export: what the family typed, never the key or its hash. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ExportReceiptsIT {

    private static final String KEY = NfceKeyFixtures.make("43", "2611");

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

    @BeforeEach
    void family() {
        doReturn(false).when(plans).legalSetEnabled();
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'markets', true)")
                .param("ws", ws)
                .update();
        market = jdbc.sql("""
                        insert into markets (workspace_id, origin, name, kind)
                        values (:ws, 'FAMILY', 'Mercado Exemplo', 'GREENGROCER') returning id""").param("ws", ws).query(UUID.class).single();
    }

    @Test
    void familyExportHasReceiptFiles() {
        saveReceipt();

        Map<String, byte[]> files = unzip(exportFamily());

        assertThat(firstLine(files.get("feira-produtos.csv"))).endsWith("id;nome;unidade;gtin;criado_em");
        assertThat(firstLine(files.get("feira-apelidos.csv"))).endsWith("mercado_id;descricao_normalizada;produto_id");
        assertThat(firstLine(files.get("feira-precos.csv")))
                .endsWith("produto_id;mercado_id;nota_id;data;unidade;quantidade;preco_unitario");
        assertThat(firstLine(files.get("feira-notas.csv"))).endsWith("id;mercado_id;data;modo;itens;criado_em");

        UUID product = jdbc.sql("select id from products where workspace_id = :ws")
                .param("ws", ws)
                .query(UUID.class)
                .single();
        UUID receipt = jdbc.sql("select id from receipts where workspace_id = :ws")
                .param("ws", ws)
                .query(UUID.class)
                .single();
        String alias = jdbc.sql("select description_norm from product_aliases where workspace_id = :ws")
                .param("ws", ws)
                .query(String.class)
                .single();
        assertThat(text(files.get("feira-produtos.csv"))).contains(product + ";Arroz 5kg;UN;;");
        assertThat(text(files.get("feira-apelidos.csv"))).contains(market + ";" + alias + ";" + product);
        assertThat(text(files.get("feira-precos.csv")))
                .contains(product + ";" + market + ";" + receipt + ";2026-11-10;UN;2,0000;4,9900");
        assertThat(text(files.get("feira-notas.csv"))).contains(receipt + ";" + market + ";2026-11-10;MANUAL;1;");

        String json = text(files.get("dados.json"));
        assertThat(JsonPath.<List<String>>read(json, "$.products[*].id")).containsExactly(product.toString());
        assertThat(JsonPath.<List<String>>read(json, "$.receipts[*].id")).containsExactly(receipt.toString());
        assertThat(JsonPath.<List<Object>>read(json, "$.prices")).hasSize(1);
        assertThat(JsonPath.<List<Object>>read(json, "$.aliases")).hasSize(1);
    }

    @Test
    void noKeyOrHashInExport() {
        saveReceipt();
        String hash = jdbc.sql("select key_hash from receipts where workspace_id = :ws")
                .param("ws", ws)
                .query(String.class)
                .single();

        Map<String, byte[]> files = unzip(exportFamily());

        assertThat(hash).isNotBlank();
        files.forEach((name, bytes) -> assertThat(text(bytes))
                .as(name)
                .doesNotContain(KEY)
                .doesNotContain(KEY.substring(0, 20))
                .doesNotContain(hash)
                .doesNotContain("key_hash")
                .doesNotContain("keyHash"));
    }

    @Test
    void marketIdsMatchMarketsCsv() {
        saveReceipt();

        Map<String, byte[]> files = unzip(exportFamily());

        List<String> marketIds = text(files.get("feira-mercados.csv"))
                .lines()
                .skip(1)
                .map(line -> line.split(";")[0])
                .toList();
        List<String> priceMarkets = text(files.get("feira-precos.csv"))
                .lines()
                .skip(1)
                .map(line -> line.split(";")[1])
                .toList();
        assertThat(priceMarkets).isNotEmpty().isSubsetOf(marketIds);
    }

    @Test
    void formatVersionBumped() {
        String json = text(unzip(exportFamily()).get("dados.json"));

        assertThat(JsonPath.<Integer>read(json, "$.formatVersion")).isEqualTo(10);
    }

    @Test
    void emptyFamilyStillHasHeaders() {
        Map<String, byte[]> files = unzip(exportFamily());

        for (String name : List.of("feira-produtos.csv", "feira-apelidos.csv", "feira-precos.csv", "feira-notas.csv")) {
            assertThat(text(files.get(name)).lines()).as(name).hasSize(1);
        }
    }

    @Test
    void personalExportHasNoReceipts() {
        saveReceipt();
        var session = TestUsers.signedIn(ana, null);
        MvcTestResult result = mvc.post()
                .uri("/v1/me/export")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();

        Map<String, byte[]> files = unzip(result);

        assertThat(files)
                .doesNotContainKeys("feira-produtos.csv", "feira-apelidos.csv", "feira-precos.csv", "feira-notas.csv");
        assertThat(JsonPath.<Map<String, Object>>read(text(files.get("dados.json")), "$"))
                .doesNotContainKeys("products", "aliases", "prices", "receipts");
    }

    private void saveReceipt() {
        String json = "{\"chave\":\"" + KEY + "\",\"version\":0,\"issuedOn\":\"2026-11-10\","
                + "\"market\":{\"ref\":\"" + market + "\"},\"items\":[{\"newProductName\":\"Arroz 5kg\","
                + "\"unit\":\"UN\",\"quantity\":\"2\",\"unitPrice\":\"4.99\"}]}";
        var session = TestUsers.signedIn(ana, null);
        assertThat(mvc.post()
                        .uri("/v1/workspaces/" + ws + "/markets/receipts")
                        .with(session)
                        .with(CsrfSession.start(mvc, session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .exchange())
                .hasStatus(201);
    }

    private MvcTestResult exportFamily() {
        var session = TestUsers.signedIn(ana, null);
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/export")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();
    }

    private static Map<String, byte[]> unzip(MvcTestResult result) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip =
                new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                files.put(entry.getName(), zip.readAllBytes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String firstLine(byte[] bytes) {
        return text(bytes).lines().findFirst().orElse("");
    }
}
