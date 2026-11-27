package br.com.liberdadefamilia.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The shopping list and the "not the same product" answers in the family's data export. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ExportShoppingListIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    private UUID ana;
    private UUID ws;

    @BeforeEach
    void family() {
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
    }

    @Test
    void listAndDismissalsExported() {
        UUID milk = product("Leite Exemplo 1 L");
        UUID coffee = product("Café Exemplo");
        UUID rice = product("Arroz Exemplo 5 kg");
        jdbc.sql("""
                        insert into shopping_list_items (workspace_id, product_id, quantity, checked_at, position,
                                                         created_at)
                        values (:ws, :milk, 2.5, timestamptz '2026-11-20 12:00:00+00', 1,
                                timestamptz '2026-11-19 08:00:00+00'),
                               (:ws, :coffee, 1, null, 2, timestamptz '2026-11-19 09:00:00+00')""")
                .param("ws", ws)
                .param("milk", milk)
                .param("coffee", coffee)
                .update();
        UUID low = milk.compareTo(rice) < 0 ? milk : rice;
        UUID high = low.equals(milk) ? rice : milk;
        jdbc.sql("insert into product_join_dismissals (workspace_id, product_a, product_b) values (:ws, :a, :b)")
                .param("ws", ws)
                .param("a", low)
                .param("b", high)
                .update();

        Map<String, byte[]> files = unzip(exportFamily());

        assertThat(firstLine(files.get("feira-lista.csv"))).endsWith("produto_id;quantidade;pego_em;posicao;criado_em");
        assertThat(text(files.get("feira-lista.csv")).lines().skip(1).toList())
                .containsExactly(
                        milk + ";2,5000;2026-11-20T12:00:00Z;1;2026-11-19T08:00:00Z",
                        coffee + ";1,0000;;2;2026-11-19T09:00:00Z");
        assertThat(firstLine(files.get("feira-nao-juntar.csv"))).endsWith("produto_a;produto_b");
        assertThat(text(files.get("feira-nao-juntar.csv")).lines().skip(1).toList())
                .containsExactly(low + ";" + high);
    }

    @Test
    void productIdsMatchProductsCsv() {
        UUID milk = product("Leite Exemplo 1 L");
        UUID coffee = product("Café Exemplo");
        jdbc.sql("""
                        insert into shopping_list_items (workspace_id, product_id, position)
                        values (:ws, :milk, 1), (:ws, :coffee, 2)""")
                .param("ws", ws)
                .param("milk", milk)
                .param("coffee", coffee)
                .update();

        Map<String, byte[]> files = unzip(exportFamily());

        List<String> known = text(files.get("feira-produtos.csv"))
                .lines()
                .skip(1)
                .map(line -> line.split(";")[0])
                .toList();
        List<String> listed = text(files.get("feira-lista.csv"))
                .lines()
                .skip(1)
                .map(line -> line.split(";")[0])
                .toList();
        assertThat(listed).hasSize(2).isSubsetOf(known);
    }

    @Test
    void emptyListStillHasHeaders() {
        Map<String, byte[]> files = unzip(exportFamily());

        assertThat(text(files.get("feira-lista.csv")).lines()).hasSize(1);
        assertThat(text(files.get("feira-nao-juntar.csv")).lines()).hasSize(1);
    }

    @Test
    void formatVersionBumped() {
        String json = text(unzip(exportFamily()).get("dados.json"));

        assertThat(JsonPath.<Integer>read(json, "$.formatVersion")).isEqualTo(11);
    }

    private UUID product(String name) {
        return jdbc.sql("insert into products (workspace_id, name) values (:ws, :name) returning id")
                .param("ws", ws)
                .param("name", name)
                .query(UUID.class)
                .single();
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
