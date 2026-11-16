package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
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

/** Editing with the optimistic lock, deleting and restoring a transaction. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransactionEditIT {

    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");
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

    private UUID anaId;
    private UUID workspaceId;
    private UUID walletId;
    private UUID mercadoId;

    @BeforeEach
    void family() {
        anaId = users.create("Ana Teste", "ana@example.com");
        workspaceId = workspaces.createFor(anaId);
        walletId = ledger.walletOf(workspaceId);
        mercadoId = ledger.categoryOf(workspaceId, "EXPENSE", "Mercado");
    }

    @Test
    void editsAllFields() {
        UUID id = expense();
        UUID bank = account("Banco", false);
        UUID salario = ledger.categoryOf(workspaceId, "INCOME", "Salário");
        Map<String, Object> body = update(0);
        body.put("kind", "INCOME");
        body.put("amount", money("99.90"));
        body.put("occurredOn", "2026-11-05");
        body.put("accountId", bank.toString());
        body.put("categoryId", salario.toString());
        body.put("description", "Bônus");

        var result = patch(id, body);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"id":"%s","kind":"INCOME","amount":{"amount":"99.90","currency":"BRL"},"occurredOn":"2026-11-05",
                 "accountId":"%s","toAccountId":null,"categoryId":"%s","description":"Bônus","version":1}""".formatted(id, bank, salario));
        assertThat(jdbc.sql("select version from transactions where id = :id")
                        .param("id", id)
                        .query(Long.class)
                        .single())
                .isOne();
    }

    /** The money only moves between the family's accounts, so the family total is back where it was. */
    @Test
    void turnsExpenseIntoTransfer() {
        UUID bank = account("Banco", false);
        BigDecimal before = familyTotal();
        UUID id = expense();
        assertThat(familyTotal()).isEqualByComparingTo(before.subtract(new BigDecimal("12.34")));
        Map<String, Object> body = update(0);
        body.put("kind", "TRANSFER");
        body.put("toAccountId", bank.toString());
        body.put("categoryId", null);

        var result = patch(id, body);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"kind":"TRANSFER","toAccountId":"%s","categoryId":null,"version":1}""".formatted(bank));
        assertThat(familyTotal()).isEqualByComparingTo(before);
    }

    /** Two tabs open the same transaction: the second save sees the first one's result. */
    @Test
    void rejectsStaleVersion() {
        UUID id = expense();
        Map<String, Object> first = update(0);
        first.put("amount", money("50.00"));
        assertThat(patch(id, first)).hasStatusOk();
        Map<String, Object> second = update(0);
        second.put("amount", money("70.00"));

        var result = patch(id, second);

        assertThat(result).hasStatus(409).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"code":"transaction.version-conflict","detail":"Este lançamento mudou desde que você abriu.",
                 "current":{"id":"%s","version":1,"amount":{"amount":"50.00","currency":"BRL"}}}""".formatted(id));
    }

    @Test
    void staleVersionWinsOverInvalidPayload() {
        UUID id = expense();
        assertThat(patch(id, update(0))).hasStatusOk();
        Map<String, Object> invalid = update(0);
        invalid.put("amount", Map.of("amount", "10.00", "currency", "USD"));

        var result = patch(id, invalid);

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.version-conflict");
    }

    @Test
    void editDeletedTransaction() {
        UUID id = expense();
        assertThat(delete(id)).hasStatus(204);

        var result = patch(id, update(1));

        assertThat(result).hasStatus(409).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"code":"transaction.deleted","detail":"Este lançamento foi excluído."}""");
    }

    /** An account archived after the fact still holds its old transactions, and they stay editable (D10). */
    @Test
    void keepsArchivedAccountWhenUnchanged() {
        UUID id = expense();
        archive("accounts", walletId);
        Map<String, Object> body = update(0);
        body.put("amount", money("20.00"));

        var result = patch(id, body);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.amount.amount").isEqualTo("20.00");
    }

    @Test
    void rejectsMovingToArchivedAccount() {
        UUID id = expense();
        Map<String, Object> body = update(0);
        body.put("accountId", account("Antiga", true).toString());

        var result = patch(id, body);

        assertThat(result).hasStatus(422).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"code":"transaction.account-not-found","detail":"Conta não encontrada nesta família."}""");
    }

    @Test
    void keepsArchivedCategoryWhenUnchanged() {
        UUID id = expense();
        archive("categories", mercadoId);
        Map<String, Object> body = update(0);
        body.put("description", "Feira");

        var result = patch(id, body);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.description").isEqualTo("Feira");
    }

    @Test
    void deleteIsIdempotent() {
        BigDecimal before = familyTotal();
        UUID id = expense();

        assertThat(delete(id)).hasStatus(204);
        assertThat(delete(id)).hasStatus(204);

        assertThat(listedIds()).doesNotContain(id.toString());
        assertThat(familyTotal()).isEqualByComparingTo(before);
    }

    @Test
    void deleteUnknown() {
        var result = delete(TestIds.v7());

        assertThat(result).hasStatus(404).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"code":"transaction.not-found","detail":"Lançamento não encontrado."}""");
    }

    @Test
    void restoreBringsItBack() {
        UUID id = expense();
        BigDecimal withExpense = familyTotal();
        assertThat(delete(id)).hasStatus(204);

        var result = restore(id);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"id":"%s","version":2}""".formatted(id));
        assertThat(listedIds()).contains(id.toString());
        assertThat(familyTotal()).isEqualByComparingTo(withExpense);
    }

    /** "Desfazer" tapped twice, or in two tabs: the second one changes nothing. */
    @Test
    void restoreIsIdempotent() {
        UUID id = expense();
        assertThat(delete(id)).hasStatus(204);
        assertThat(restore(id)).hasStatusOk();

        var again = restore(id);

        assertThat(again).hasStatusOk();
        assertThat(again).bodyJson().extractingPath("$.version").isEqualTo(2);
        assertThat(listedIds().stream().filter(id.toString()::equals)).hasSize(1);
    }

    @Test
    void restoreUnknown() {
        var result = restore(TestIds.v7());

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.not-found");
    }

    @Test
    void editUnknown() {
        var result = patch(TestIds.v7(), update(0));

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.not-found");
    }

    @Test
    void editRunsTheFamilyRules() {
        UUID id = expense();
        Map<String, Object> body = update(0);
        body.put("occurredOn", "1999-12-31");

        var result = patch(id, body);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.date-out-of-range");
    }

    @Test
    void editNeedsEveryField() {
        UUID id = expense();
        Map<String, Object> body = update(0);
        body.remove("version");
        body.remove("amount");

        var result = patch(id, body);

        assertThat(result).hasStatus(422);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .contains("version", "amount");
    }

    @Test
    void viewerCannotEditOrDelete() {
        UUID id = expense();
        UUID viewerId = users.create("Bia Teste", "bia@example.com");
        workspaces.addMember(workspaceId, viewerId, Role.VIEWER);

        List<MvcTestResult> results = List.of(
                send("PATCH", id, "", update(0), viewerId),
                send("DELETE", id, "", null, viewerId),
                send("POST", id, "/restore", null, viewerId));

        for (MvcTestResult result : results) {
            assertThat(result).hasStatus(403);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
        }
        assertThat(jdbc.sql("select version from transactions where id = :id and deleted_at is null")
                        .param("id", id)
                        .query(Long.class)
                        .single())
                .isZero();
    }

    /** Ana's 12.34 BRL market expense from the wallet on 2026-11-02, created through the API. */
    private UUID expense() {
        UUID id = TestIds.v7();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id.toString());
        body.put("kind", "EXPENSE");
        body.put("amount", money("12.34"));
        body.put("occurredOn", "2026-11-02");
        body.put("accountId", walletId.toString());
        body.put("categoryId", mercadoId.toString());
        var result = mvc.post()
                .uri("/v1/workspaces/" + workspaceId + "/transactions")
                .with(TestUsers.signedIn(anaId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(anaId, null)))
                .header("Idempotency-Key", id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))
                .exchange();
        assertThat(result).hasStatus(201);
        return id;
    }

    /** The full replacement that keeps the default expense as it is, at the given version. */
    private Map<String, Object> update(long version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        body.put("kind", "EXPENSE");
        body.put("amount", money("12.34"));
        body.put("occurredOn", "2026-11-02");
        body.put("accountId", walletId.toString());
        body.put("categoryId", mercadoId.toString());
        return body;
    }

    private static Map<String, String> money(String amount) {
        return Map.of("amount", amount, "currency", "BRL");
    }

    /** A BRL checking account of the family with no starting balance, archived or not. */
    private UUID account(String name, boolean archived) {
        return jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, archived_at)
                        values (:workspaceId, :name, 'CHECKING', 'BRL', case when :archived then now() end)
                        returning id""")
                .param("workspaceId", workspaceId)
                .param("name", name)
                .param("archived", archived)
                .query(UUID.class)
                .single();
    }

    private void archive(String table, UUID id) {
        jdbc.sql("update " + table + " set archived_at = now() where id = :id")
                .param("id", id)
                .update();
    }

    /** The sum of every account balance, as {@code GET /accounts} shows them. */
    private BigDecimal familyTotal() {
        var result = mvc.get()
                .uri("/v1/workspaces/" + workspaceId + "/accounts")
                .with(TestUsers.signedIn(anaId, null))
                .exchange();
        assertThat(result).hasStatusOk();
        List<String> balances = JsonPath.read(body(result), "$[*].balance.amount");
        return balances.stream().map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<String> listedIds() {
        var result = mvc.get()
                .uri("/v1/workspaces/" + workspaceId + "/transactions?month=2026-11")
                .with(TestUsers.signedIn(anaId, null))
                .exchange();
        assertThat(result).hasStatusOk();
        return JsonPath.read(body(result), "$[*].id");
    }

    private MvcTestResult patch(UUID id, Map<String, Object> body) {
        return send("PATCH", id, "", body, anaId);
    }

    private MvcTestResult delete(UUID id) {
        return send("DELETE", id, "", null, anaId);
    }

    private MvcTestResult restore(UUID id) {
        return send("POST", id, "/restore", null, anaId);
    }

    private MvcTestResult send(
            String method, UUID id, String suffix, @Nullable Map<String, Object> body, UUID callerId) {
        var request = switch (method) {
            case "PATCH" -> mvc.patch();
            case "DELETE" -> mvc.delete();
            default -> mvc.post();
        };
        request.uri("/v1/workspaces/" + workspaceId + "/transactions/" + id + suffix)
                .with(TestUsers.signedIn(callerId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(callerId, null)));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        }
        return request.exchange();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
