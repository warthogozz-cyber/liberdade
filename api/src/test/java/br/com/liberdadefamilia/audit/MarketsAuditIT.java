package br.com.liberdadefamilia.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.markets.MarketEdited;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The Feira leaves a trace of what changed and never of what it changed to: no link, point, address or name. Every
 * action here is driven through the real routes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MarketsAuditIT {

    private static final String MARKET_NAME = "Feira Exemplo do Bairro";
    private static final String HOME_LABEL = "Casa Exemplo";
    private static final Pattern COORDINATE = Pattern.compile("\\d+\\.\\d{3}");

    private record Row(
            String action, String targetType, @Nullable UUID targetId, UUID actorUserId, String metadata) {}

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
    void actionsRecorded() {
        UUID market = exercise();

        List<Row> rows = rows();
        assertThat(rows)
                .extracting(Row::action)
                .containsExactly(
                        "market.added",
                        "market.edited",
                        "market.edited",
                        "market.edited",
                        "market.edited",
                        "market_home.set",
                        "market_home.cleared");
        for (Row row : rows) {
            assertThat(row.actorUserId()).isEqualTo(ana);
            if (row.action().startsWith("market_home")) {
                assertThat(row.targetType()).isEqualTo("WORKSPACE");
                assertThat(row.targetId()).isEqualTo(ws);
                assertThat(row.metadata()).isEqualTo("{}");
            } else {
                assertThat(row.targetType()).isEqualTo("MARKET");
            }
        }
        assertThat(rows.get(0).targetId()).isEqualTo(market);
        assertThat(rows.get(0).metadata()).isEqualTo("{}");

        List<Row> edits =
                rows.stream().filter(r -> r.action().equals("market.edited")).toList();
        for (Row edit : edits) {
            Map<String, Object> metadata = JsonPath.read(edit.metadata(), "$");
            assertThat(metadata.keySet()).isEqualTo(Set.of("change"));
        }
        assertThat(edits)
                .extracting(r -> JsonPath.<String>read(r.metadata(), "$.change"))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(MarketEdited.Change.values())
                        .map(Enum::name)
                        .toList());
    }

    @Test
    void noUrlsInMetadata() {
        exercise();

        List<Row> rows = rows();
        assertThat(rows).isNotEmpty();
        for (Row row : rows) {
            Map<String, Object> metadata = JsonPath.read(row.metadata(), "$");
            for (Object value : metadata.values()) {
                String text = String.valueOf(value);
                assertThat(text)
                        .as(row.action())
                        .doesNotContain("http")
                        .doesNotContain("instagram")
                        .doesNotContain("encarte")
                        .doesNotContain(MARKET_NAME)
                        .doesNotContain(HOME_LABEL)
                        .doesNotContain("Rua Exemplo");
                assertThat(COORDINATE.matcher(text).find()).as(row.action()).isFalse();
            }
        }
        assertThat(jdbc.sql("""
                                select count(*) from audit_events
                                where workspace_id = :ws and action like 'market%'
                                  and (metadata::text like '%http%' or metadata::text ~ '\\d+\\.\\d{3}'
                                       or metadata::text like :name or metadata::text like :label)""")
                        .param("ws", ws)
                        .param("name", "%" + MARKET_NAME + "%")
                        .param("label", "%" + HOME_LABEL + "%")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    /** One of each action, through the routes; returns the id of the market the family added. */
    private UUID exercise() {
        MvcTestResult added = send(
                HttpMethod.POST,
                "/markets",
                "{\"name\":\"" + MARKET_NAME
                        + "\",\"kind\":\"SUPERMARKET\",\"lat\":\"-23.00300\",\"lon\":\"-45.99900\"}");
        assertThat(added).hasStatus(201);
        UUID market = UUID.fromString(JsonPath.read(body(added), "$.ref"));

        assertThat(send(HttpMethod.PUT, "/markets/" + market + "/links", """
                        {"website":"https://familia.example.com/mercado","instagram":"https://www.instagram.com/exemplo/",
                         "flyerUrl":"https://encarte.example.com/a","flyerSource":"site do mercado",
                         "flyerValidUntil":null}""")).hasStatus(200);
        assertThat(send(
                        HttpMethod.PUT,
                        "/markets/" + market + "/position",
                        "{\"lat\":\"-23.00400\",\"lon\":\"-45.99800\"}"))
                .hasStatus(200);
        assertThat(send(HttpMethod.POST, "/markets/" + market + "/archive", null))
                .hasStatus(204);

        UUID left = jdbc.sql("""
                        insert into markets (workspace_id, origin, osm_type, osm_id)
                        values (:ws, 'OSM', 'n', 987000111) returning id""").param("ws", ws).query(UUID.class).single();
        assertThat(send(
                        HttpMethod.POST,
                        "/markets/" + left + "/convert",
                        "{\"name\":\"" + MARKET_NAME
                                + " Dois\",\"kind\":\"SUPERMARKET\",\"lat\":\"-23.00500\",\"lon\":\"-45.99700\"}"))
                .hasStatus(200);

        assertThat(send(
                        HttpMethod.PUT,
                        "/markets/home",
                        "{\"lat\":\"-23.123\",\"lon\":\"-46.456\",\"label\":\"" + HOME_LABEL + "\"}"))
                .hasStatus(200);
        assertThat(send(HttpMethod.DELETE, "/markets/home", null)).hasStatus(204);
        return market;
    }

    private List<Row> rows() {
        return jdbc.sql("""
                        select action, target_type, target_id, actor_user_id, metadata::text as metadata
                        from audit_events
                        where workspace_id = :ws and action like 'market%'
                        order by occurred_at, id""")
                .param("ws", ws)
                .query((rs, row) -> new Row(
                        rs.getString("action"),
                        rs.getString("target_type"),
                        rs.getObject("target_id", UUID.class),
                        rs.getObject("actor_user_id", UUID.class),
                        rs.getString("metadata")))
                .list();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult send(HttpMethod method, String path, @Nullable String body) {
        var session = TestUsers.signedIn(ana, null);
        var request = mvc.method(method).uri("/v1/workspaces/" + ws + path).with(session);
        if (method != HttpMethod.GET) {
            request.with(CsrfSession.start(mvc, session));
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }
}
