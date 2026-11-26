package br.com.liberdadefamilia.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.markets.domain.NfceKeyFixtures;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
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

/** A saved receipt leaves a trace of how it came and how many items it had, never of what was on it. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReceiptsAuditIT {

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
    void metadataOnlyModeAndCount() {
        UUID market = jdbc.sql("""
                        insert into markets (workspace_id, origin, name, kind)
                        values (:ws, 'FAMILY', 'Mercado Exemplo', 'GREENGROCER') returning id""").param("ws", ws).query(UUID.class).single();
        String json = "{\"chave\":\"" + KEY + "\",\"version\":0,\"issuedOn\":\"2026-11-10\","
                + "\"market\":{\"ref\":\"" + market + "\"},\"items\":["
                + "{\"newProductName\":\"Produto Secreto\",\"unit\":\"UN\",\"quantity\":\"1\",\"unitPrice\":\"4.9876\"},"
                + "{\"newProductName\":\"Outro Item\",\"unit\":\"UN\",\"quantity\":\"1\",\"unitPrice\":\"7.50\"}]}";
        var session = TestUsers.signedIn(ana, null);
        assertThat(mvc.post()
                        .uri("/v1/workspaces/" + ws + "/markets/receipts")
                        .with(session)
                        .with(CsrfSession.start(mvc, session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .exchange())
                .hasStatus(201);

        UUID receipt = jdbc.sql("select id from receipts where workspace_id = :ws")
                .param("ws", ws)
                .query(UUID.class)
                .single();
        var row = jdbc.sql("""
                        select target_type, target_id, actor_user_id, metadata::text as metadata,
                               metadata = '{"mode": "MANUAL", "itemCount": "2"}'::jsonb as exact
                        from audit_events where workspace_id = :ws and action = 'receipt.added'""").param("ws", ws).query().singleRow();
        assertThat(row.get("target_type")).isEqualTo("RECEIPT");
        assertThat(row.get("target_id")).isEqualTo(receipt);
        assertThat(row.get("actor_user_id")).isEqualTo(ana);
        assertThat(row.get("exact")).as(String.valueOf(row.get("metadata"))).isEqualTo(true);
        assertThat(row.toString())
                .doesNotContain(KEY.substring(0, 20))
                .doesNotContain("Produto Secreto")
                .doesNotContain("4.9876");
    }
}
