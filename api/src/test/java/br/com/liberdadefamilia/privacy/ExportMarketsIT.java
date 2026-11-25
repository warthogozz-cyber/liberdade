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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The Feira in the data export: what the family has, never what OpenStreetMap knows about a market. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ExportMarketsIT {

    private static final long OSM_ID = 987654321L;
    private static final String OSM_NAME = "Armazem Osm Unico";

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
        jdbc.sql("""
                        insert into osm_markets (osm_type, osm_id, kind, name, lat, lon, street, city)
                        values ('n', :id, 'SUPERMARKET', :name, -23.45678, -46.54321, 'Rua Exemplo', 'Cidade Exemplo')""").param("id", OSM_ID).param("name", OSM_NAME).update();
    }

    @AfterEach
    void clean() {
        jdbc.sql("delete from osm_markets where osm_id = :id")
                .param("id", OSM_ID)
                .update();
    }

    @Test
    void familyExportHasFeiraFiles() {
        UUID own = jdbc.sql("""
                        insert into markets (workspace_id, origin, name, kind, lat, lon, cnpj, website, instagram,
                                             flyer_url, flyer_source, flyer_valid_until)
                        values (:ws, 'FAMILY', 'Feira Exemplo; do Bairro', 'GREENGROCER', -23.00300, -45.99900,
                                '12345678000195', 'https://familia.example.com', 'https://www.instagram.com/exemplo/',
                                'https://encarte.example.com/a', 'site do mercado', date '2026-12-31')
                        returning id""").param("ws", ws).query(UUID.class).single();
        UUID osm = jdbc.sql("""
                        insert into markets (workspace_id, origin, osm_type, osm_id, archived_at)
                        values (:ws, 'OSM', 'n', :id, timestamptz '2026-11-20 12:00:00+00') returning id""")
                .param("ws", ws)
                .param("id", OSM_ID)
                .query(UUID.class)
                .single();
        jdbc.sql("insert into market_home (workspace_id, lat, lon, label) values (:ws, -23.123, -46.456, 'Casa')")
                .param("ws", ws)
                .update();

        Map<String, byte[]> files = unzip(exportFamily(ana, ws));

        assertThat(firstLine(files.get("feira-mercados.csv")))
                .endsWith("id;origem;ref_osm;nome;tipo;lat;lon;cnpj;site;instagram;encarte;fonte_encarte;"
                        + "encarte_valido_ate;arquivado_em");
        String markets = text(files.get("feira-mercados.csv"));
        assertThat(markets)
                .contains(own + ";FAMILY;;\"Feira Exemplo; do Bairro\";Hortifruti;-23,00300;-45,99900;12345678000195;"
                        + "https://familia.example.com;https://www.instagram.com/exemplo/;"
                        + "https://encarte.example.com/a;site do mercado;2026-12-31;")
                .contains(osm + ";OSM;osm-n-" + OSM_ID + ";;;;;;;;;;;2026-11-20T12:00:00Z");
        assertThat(firstLine(files.get("feira-endereco.csv"))).endsWith("lat;lon;rotulo");
        assertThat(text(files.get("feira-endereco.csv"))).contains("-23,123;-46,456;Casa");

        String json = text(files.get("dados.json"));
        assertThat(JsonPath.<List<String>>read(json, "$.markets[*].id"))
                .containsExactly(own.toString(), osm.toString());
        assertThat(JsonPath.<String>read(json, "$.marketHome.label")).isEqualTo("Casa");
    }

    @Test
    void noOsmDataInExport() {
        jdbc.sql("""
                        insert into markets (workspace_id, origin, osm_type, osm_id, website)
                        values (:ws, 'OSM', 'n', :id, 'https://familia.example.com')""").param("ws", ws).param("id", OSM_ID).update();

        Map<String, byte[]> files = unzip(exportFamily(ana, ws));

        assertThat(files).containsKey("feira-mercados.csv");
        files.forEach((name, bytes) -> assertThat(text(bytes))
                .as(name)
                .doesNotContain(OSM_NAME)
                .doesNotContain("-23,45678")
                .doesNotContain("-23.45678")
                .doesNotContain("Rua Exemplo"));
    }

    @Test
    void emptyFeiraStillHasHeaders() {
        Map<String, byte[]> files = unzip(exportFamily(ana, ws));

        assertThat(text(files.get("feira-mercados.csv")).lines()).hasSize(1);
        assertThat(text(files.get("feira-endereco.csv")).lines()).hasSize(1);
        assertThat(JsonPath.<Object>read(text(files.get("dados.json")), "$.marketHome"))
                .isNull();
    }

    @Test
    void personalExportHasNoFeira() {
        jdbc.sql("insert into market_home (workspace_id, lat, lon) values (:ws, -23.123, -46.456)")
                .param("ws", ws)
                .update();

        Map<String, byte[]> files = unzip(export("/v1/me/export", ana));

        assertThat(files).doesNotContainKeys("feira-mercados.csv", "feira-endereco.csv");
        assertThat(JsonPath.<Map<String, Object>>read(text(files.get("dados.json")), "$"))
                .doesNotContainKeys("markets", "marketHome");
    }

    @Test
    void formatVersionBumped() {
        String json = text(unzip(exportFamily(ana, ws)).get("dados.json"));

        assertThat(JsonPath.<Integer>read(json, "$.formatVersion")).isEqualTo(9);
    }

    private MvcTestResult exportFamily(UUID user, UUID workspace) {
        return export("/v1/workspaces/" + workspace + "/export", user);
    }

    private MvcTestResult export(String uri, UUID user) {
        var session = TestUsers.signedIn(user, null);
        return mvc.post()
                .uri(uri)
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
