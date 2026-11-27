package br.com.liberdadefamilia.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

/** A join leaves a trace of which product stayed and which went, never of names; the shopping list leaves none. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductsAuditIT {

    private static final String KEPT_NAME = "Leite Exemplo 1 L";
    private static final String REMOVED_NAME = "Leite Ex. 1L";

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
    void mergeAudited() {
        UUID kept = product(KEPT_NAME);
        UUID removed = product(REMOVED_NAME);

        assertThat(send(HttpMethod.POST, "/products/" + kept + "/merge", "{\"removeId\":\"" + removed + "\"}"))
                .hasStatus(200);

        List<Map<String, Object>> rows = jdbc.sql("""
                        select action, target_type, target_id, actor_user_id, metadata::text as metadata
                        from audit_events where workspace_id = :ws and action like 'product%'""").param("ws", ws).query().listOfRows();
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("action")).isEqualTo("products.merged");
        assertThat(row.get("target_type")).isEqualTo("PRODUCT");
        assertThat(row.get("target_id")).isEqualTo(kept);
        assertThat(row.get("actor_user_id")).isEqualTo(ana);
        String metadata = (String) row.get("metadata");
        Map<String, Object> parsed = JsonPath.read(metadata, "$");
        assertThat(parsed.keySet()).isEqualTo(Set.of("removedProductId"));
        assertThat(parsed.get("removedProductId")).isEqualTo(removed.toString());
        assertThat(metadata).doesNotContain("Leite");
    }

    @Test
    void listNotAudited() {
        UUID milk = product(KEPT_NAME);
        long before = auditRows();

        MvcTestResult added = send(HttpMethod.POST, "/list", "{\"productId\":\"" + milk + "\"}");
        assertThat(added).hasStatus(201);
        UUID item = UUID.fromString(JsonPath.read(body(added), "$.id"));
        assertThat(send(HttpMethod.PATCH, "/list/" + item, "{\"checked\":true}"))
                .hasStatus(200);
        assertThat(send(HttpMethod.POST, "/list/clear-checked", null)).hasStatus(200);

        assertThat(auditRows()).isEqualTo(before);
    }

    private long auditRows() {
        return jdbc.sql("select count(*) from audit_events where workspace_id = :ws")
                .param("ws", ws)
                .query(Long.class)
                .single();
    }

    private UUID product(String name) {
        return jdbc.sql("insert into products (workspace_id, name) values (:ws, :name) returning id")
                .param("ws", ws)
                .param("name", name)
                .query(UUID.class)
                .single();
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
        var request = mvc.method(method)
                .uri("/v1/workspaces/" + ws + "/markets" + path)
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(body);
        }
        return request.exchange();
    }
}
