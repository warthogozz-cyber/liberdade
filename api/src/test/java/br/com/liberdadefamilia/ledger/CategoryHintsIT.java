package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The category the family used last for a description of a statement. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "liberdade.billing.enabled=false")
class CategoryHintsIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestLedger ledger;

    private UUID owner;
    private UUID ws;
    private UUID wallet;
    private UUID membership;
    private UUID mercado;
    private UUID lazer;
    private LocalDate today;

    @BeforeEach
    void family() {
        owner = users.create("Dono Teste", null);
        ws = workspaces.createFor(owner);
        wallet = ledger.walletOf(ws);
        membership = workspaces.membershipOf(ws, owner);
        mercado = ledger.categoryOf(ws, "EXPENSE", "Mercado");
        lazer = ledger.categoryOf(ws, "EXPENSE", "Lazer");
        today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    @Test
    void newestMatchWins() {
        insert("EXPENSE", "Padaria São João", mercado, today.minusDays(10), "CONFIRMED", false);
        insert("EXPENSE", "Padaria São João", lazer, today.minusDays(2), "CONFIRMED", false);

        assertThat(hints(item("EXPENSE", "padaria sao joao"))).containsExactly(lazer);
    }

    @Test
    void kindMatters() {
        UUID salario = ledger.categoryOf(ws, "INCOME", "Salário");
        insert("INCOME", "Padaria São João", salario, today.minusDays(2), "CONFIRMED", false);

        assertThat(hints(item("EXPENSE", "padaria sao joao"))).containsExactly((UUID) null);
        assertThat(hints(item("INCOME", "padaria sao joao"))).containsExactly(salario);
    }

    @Test
    void ignoresDeletedPlannedArchivedAndOld() {
        UUID arquivada = jdbc.sql("""
                        insert into categories (workspace_id, name, kind, archived_at)
                        values (:ws, 'Antiga', 'EXPENSE', now()) returning id""").param("ws", ws).query(UUID.class).single();
        insert("EXPENSE", "excluido", lazer, today.minusDays(1), "CONFIRMED", true);
        insert("EXPENSE", "previsto", lazer, today.minusDays(1), "PLANNED", false);
        insert("EXPENSE", "arquivada", arquivada, today.minusDays(1), "CONFIRMED", false);
        insert("EXPENSE", "antigo", lazer, today.minusDays(366), "CONFIRMED", false);

        assertThat(hints(
                        item("EXPENSE", "excluido"),
                        item("EXPENSE", "previsto"),
                        item("EXPENSE", "arquivada"),
                        item("EXPENSE", "antigo")))
                .containsExactly(null, null, null, null);
    }

    @Test
    void keepsTheOrderOfTheRequest() {
        insert("EXPENSE", "Padaria", mercado, today.minusDays(3), "CONFIRMED", false);
        insert("EXPENSE", "Cinema", lazer, today.minusDays(3), "CONFIRMED", false);

        assertThat(hints(item("EXPENSE", "cinema"), item("EXPENSE", "sem historico"), item("EXPENSE", "padaria")))
                .containsExactly(lazer, null, mercado);
        assertThat(hints(item("EXPENSE", ""))).containsExactly((UUID) null);
    }

    @Test
    void rejects501Items() {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            items.add(item("EXPENSE", "x" + i));
        }

        // Validation failures are 422 request.invalid across the API.
        assertThat(call(items)).hasStatus(422);
        assertThat(call(items.subList(0, 500))).hasStatusOk();
        assertThat(call(List.of())).hasStatus(422);
    }

    private static String item(String kind, String key) {
        return "{\"kind\":\"" + kind + "\",\"key\":\"" + key + "\"}";
    }

    private List<@Nullable UUID> hints(String... items) {
        MvcTestResult result = call(Arrays.asList(items));
        assertThat(result).hasStatusOk();
        String json = body(result);
        List<@Nullable UUID> ids = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            String id = JsonPath.read(json, "$.hints[" + i + "].categoryId");
            ids.add(id == null ? null : UUID.fromString(id));
        }
        return ids;
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult call(List<String> items) {
        var session = TestUsers.signedIn(owner, null);
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/imports/category-hints")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[" + String.join(",", items) + "]}")
                .exchange();
    }

    private void insert(String kind, String description, UUID category, LocalDate on, String status, boolean deleted) {
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on, account_id, category_id,
                             description, member_id, created_by, status, deleted_at, scheduled_on)
                        values (:id, :ws, :kind, 10, 'BRL', :on, :account, :category, :description, :m, :m, :status,
                                case when :deleted then now() end,
                                case when :status = 'PLANNED' then :on end)""")
                .param("id", TestIds.v7())
                .param("ws", ws)
                .param("kind", kind)
                .param("on", on)
                .param("account", wallet)
                .param("category", category)
                .param("description", description)
                .param("m", membership)
                .param("status", status)
                .param("deleted", deleted)
                .update();
    }
}
