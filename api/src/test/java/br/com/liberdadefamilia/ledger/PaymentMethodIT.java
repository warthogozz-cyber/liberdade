package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/** How a transaction's payment method is recorded, edited, filtered and kept out of transfers. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PaymentMethodIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    private UUID userId;
    private UUID workspaceId;
    private UUID walletId;
    private UUID mercadoId;
    private UUID salarioId;

    @BeforeEach
    void family() {
        userId = users.create("Ana Teste", "ana@example.com");
        workspaceId = workspaces.createFor(userId);
        walletId = ledger.walletOf(workspaceId);
        mercadoId = ledger.categoryOf(workspaceId, "EXPENSE", "Mercado");
        salarioId = ledger.categoryOf(workspaceId, "INCOME", "Salário");
    }

    @Test
    void createAndReadBack() {
        UUID id = TestIds.v7();

        var created = post(id, body(id, "EXPENSE", "12.34", mercadoId, "PIX"));

        assertThat(created).hasStatus(201);
        assertThat(created).bodyJson().extractingPath("$.paymentMethod").isEqualTo("PIX");
        var read = get("/transactions/" + id);
        assertThat(read).hasStatusOk();
        assertThat(read).bodyJson().extractingPath("$.paymentMethod").isEqualTo("PIX");
    }

    @Test
    void transferRejectsPaymentMethod() {
        UUID other = jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency)
                        values (:ws, 'Poupança', 'SAVINGS', 'BRL') returning id""").param("ws", workspaceId).query(UUID.class).single();
        UUID id = TestIds.v7();
        Map<String, Object> transfer = body(id, "TRANSFER", "50.00", null, "CASH");
        transfer.put("toAccountId", other.toString());

        var result = post(id, transfer);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"code":"transaction.payment-method-transfer","detail":"Transferência não tem forma de pagamento."}""");
    }

    @Test
    void patchNullClears() {
        UUID id = TestIds.v7();
        assertThat(post(id, body(id, "EXPENSE", "12.34", mercadoId, "PIX"))).hasStatus(201);
        Map<String, Object> edit = body(id, "EXPENSE", "12.34", mercadoId, null);
        edit.remove("id");
        edit.put("version", 0);

        var result = patch(id, edit);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.paymentMethod").isNull();
    }

    @Test
    void retryWithOtherMethodConflicts() {
        UUID id = TestIds.v7();
        assertThat(post(id, body(id, "EXPENSE", "12.34", mercadoId, "PIX"))).hasStatus(201);

        var retry = post(id, body(id, "EXPENSE", "12.34", mercadoId, "CASH"));

        assertThat(retry).hasStatus(409);
        assertThat(retry).bodyJson().extractingPath("$.code").isEqualTo("transaction.id-conflict");
        assertThat(post(id, body(id, "EXPENSE", "12.34", mercadoId, "PIX"))).hasStatus(200);
    }

    @Test
    void listFiltersByMethod() {
        UUID pix = TestIds.v7();
        UUID cash = TestIds.v7();
        assertThat(post(pix, body(pix, "EXPENSE", "10.00", mercadoId, "PIX"))).hasStatus(201);
        assertThat(post(cash, body(cash, "EXPENSE", "20.00", mercadoId, "CASH")))
                .hasStatus(201);

        var result = get("/transactions?month=2026-11&paymentMethod=PIX");

        assertThat(result).hasStatusOk();
        List<String> ids = JsonPath.read(text(result), "$[*].id");
        assertThat(ids).containsExactly(pix.toString());
    }

    @Test
    void dashboardFiltersFlowsNotBalance() {
        UUID income = TestIds.v7();
        UUID expense = TestIds.v7();
        assertThat(post(income, body(income, "INCOME", "100.00", salarioId, "PIX")))
                .hasStatus(201);
        assertThat(post(expense, body(expense, "EXPENSE", "30.00", mercadoId, "CASH")))
                .hasStatus(201);

        var result = get("/dashboard?month=2026-11&paymentMethod=CASH");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"income":{"amount":"0.00","currency":"BRL"},
                 "expenses":{"amount":"30.00","currency":"BRL"},
                 "result":{"amount":"-30.00","currency":"BRL"},
                 "balance":{"amount":"70.00","currency":"BRL"}}""");
        List<String> categories = JsonPath.read(text(result), "$.byCategory[*].categoryId");
        assertThat(categories).containsExactly(mercadoId.toString());
    }

    private Map<String, Object> body(
            UUID id, String kind, String amount, @Nullable UUID categoryId, @Nullable String paymentMethod) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id.toString());
        body.put("kind", kind);
        body.put("amount", Map.of("amount", amount, "currency", "BRL"));
        body.put("occurredOn", "2026-11-02");
        body.put("accountId", walletId.toString());
        if (categoryId != null) {
            body.put("categoryId", categoryId.toString());
        }
        if (paymentMethod != null) {
            body.put("paymentMethod", paymentMethod);
        }
        return body;
    }

    private MvcTestResult post(UUID id, Map<String, Object> body) {
        return mvc.post()
                .uri("/v1/workspaces/" + workspaceId + "/transactions")
                .with(TestUsers.signedIn(userId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(userId, null)))
                .header("Idempotency-Key", id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))
                .exchange();
    }

    private MvcTestResult patch(UUID id, Map<String, Object> body) {
        return mvc.patch()
                .uri("/v1/workspaces/" + workspaceId + "/transactions/" + id)
                .with(TestUsers.signedIn(userId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(userId, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))
                .exchange();
    }

    private MvcTestResult get(String path) {
        return mvc.get()
                .uri("/v1/workspaces/" + workspaceId + path)
                .with(TestUsers.signedIn(userId, null))
                .exchange();
    }

    private static String text(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
