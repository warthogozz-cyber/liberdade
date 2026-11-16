package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.time.LocalDate;
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

/** A member of one family reaches nothing of another, and cannot tell it exists (ADR-0008). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IsolationIT {

    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

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
    private UUID anaWorkspace;
    private UUID biaId;
    private UUID biaWorkspace;

    @BeforeEach
    void families() {
        anaId = users.create("Ana Teste", "ana@example.com");
        anaWorkspace = workspaces.createFor(anaId);
        ledger.expense(
                anaWorkspace,
                workspaces.membershipOf(anaWorkspace, anaId),
                ledger.categoryOf(anaWorkspace, "EXPENSE", "Mercado"),
                "12.34",
                LocalDate.of(2026, 11, 2));
        biaId = users.create("Bia Teste", "bia@example.com");
        biaWorkspace = workspaces.createFor(biaId);
    }

    @Test
    void accounts() {
        assertNotFound(get("/accounts"));
    }

    @Test
    void categories() {
        assertNotFound(get("/categories?kind=EXPENSE"));
    }

    @Test
    void transactions() {
        assertNotFound(get("/transactions?month=2026-11"));
    }

    @Test
    void dashboard() {
        assertNotFound(get("/dashboard?month=2026-11"));
    }

    @Test
    void newTransaction() {
        UUID id = TestIds.v7();

        var result = mvc.post()
                .uri("/v1/workspaces/" + anaWorkspace + "/transactions")
                .with(TestUsers.signedIn(biaId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(biaId, null)))
                .header("Idempotency-Key", id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id":"%s","kind":"EXPENSE","amount":{"amount":"12.34","currency":"BRL"},
                         "occurredOn":"2026-11-02","accountId":"%s","categoryId":"%s"}""".formatted(
                        id, ledger.walletOf(biaWorkspace), ledger.categoryOf(biaWorkspace, "EXPENSE", "Mercado")))
                .exchange();

        assertNotFound(result);
        assertThat(jdbc.sql("select count(*) from transactions where id = :id")
                        .param("id", id)
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void patchTransaction() {
        String body =
                """
                {"version":0,"kind":"EXPENSE","amount":{"amount":"99.00","currency":"BRL"},"occurredOn":"2026-11-02",
                 "accountId":"%s","categoryId":"%s"}""".formatted(ledger.walletOf(biaWorkspace), ledger.categoryOf(biaWorkspace, "EXPENSE", "Mercado"));

        assertForeignTransactionUntouched("PATCH", "", body);
    }

    @Test
    void deleteTransaction() {
        assertForeignTransactionUntouched("DELETE", "", null);
    }

    @Test
    void restoreTransaction() {
        jdbc.sql("update transactions set deleted_at = now() where id = :id")
                .param("id", anaTransaction())
                .update();

        assertForeignTransactionUntouched("POST", "/restore", null);
    }

    /** Ana moves money from her wallet into Bia's: the destination is not one of Ana's accounts. */
    @Test
    void transferToForeignAccount() {
        UUID id = TestIds.v7();

        var result = mvc.post()
                .uri("/v1/workspaces/" + anaWorkspace + "/transactions")
                .with(TestUsers.signedIn(anaId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(anaId, null)))
                .header("Idempotency-Key", id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id":"%s","kind":"TRANSFER","amount":{"amount":"12.34","currency":"BRL"},
                         "occurredOn":"2026-11-02","accountId":"%s","toAccountId":"%s"}""".formatted(id, ledger.walletOf(anaWorkspace), ledger.walletOf(biaWorkspace)))
                .exchange();

        assertThat(result).hasStatus(422).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.account-not-found");
        assertThat(jdbc.sql("select count(*) from transactions where id = :id")
                        .param("id", id)
                        .query(Long.class)
                        .single())
                .isZero();
    }

    /** Bia names Ana's transaction under her own family: it is not found and nothing of it changes. */
    private void assertForeignTransactionUntouched(String method, String suffix, @Nullable String body) {
        UUID anaTransaction = anaTransaction();
        String before = transactionRow(anaTransaction);
        var request = switch (method) {
            case "PATCH" -> mvc.patch();
            case "DELETE" -> mvc.delete();
            default -> mvc.post();
        };
        request.uri("/v1/workspaces/" + biaWorkspace + "/transactions/" + anaTransaction + suffix)
                .with(TestUsers.signedIn(biaId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(biaId, null)));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }

        var result = request.exchange();

        assertThat(result).hasStatus(404).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.not-found");
        assertThat(transactionRow(anaTransaction)).isEqualTo(before);
    }

    private UUID anaTransaction() {
        return jdbc.sql("select id from transactions where workspace_id = :workspaceId")
                .param("workspaceId", anaWorkspace)
                .query(UUID.class)
                .single();
    }

    private String transactionRow(UUID transactionId) {
        return jdbc.sql("select cast(t as text) from transactions t where id = :id")
                .param("id", transactionId)
                .query(String.class)
                .single();
    }

    @Test
    void patchAccount() {
        assertForeignAccountUntouched("PATCH", "", "{\"name\":\"Invadida\"}");
    }

    @Test
    void archiveAccount() {
        assertForeignAccountUntouched("POST", "/archive", null);
    }

    @Test
    void unarchiveAccount() {
        jdbc.sql("update accounts set archived_at = now() where id = :id")
                .param("id", ledger.walletOf(anaWorkspace))
                .update();

        assertForeignAccountUntouched("POST", "/unarchive", null);
    }

    /** Bia names Ana's account under her own family: the account is not found and nothing of it changes. */
    private void assertForeignAccountUntouched(String method, String suffix, String body) {
        UUID anaAccount = ledger.walletOf(anaWorkspace);
        String before = accountRow(anaAccount);
        var request = method.equals("PATCH") ? mvc.patch() : mvc.post();
        request.uri("/v1/workspaces/" + biaWorkspace + "/accounts/" + anaAccount + suffix)
                .with(TestUsers.signedIn(biaId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(biaId, null)));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }

        var result = request.exchange();

        assertThat(result).hasStatus(404).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("account.not-found");
        assertThat(accountRow(anaAccount)).isEqualTo(before);
    }

    private String accountRow(UUID accountId) {
        return jdbc.sql("select cast(a as text) from accounts a where id = :id")
                .param("id", accountId)
                .query(String.class)
                .single();
    }

    @Test
    void patchCategory() {
        assertForeignCategoryUntouched("PATCH", "", "{\"name\":\"Invadida\"}");
    }

    @Test
    void archiveCategory() {
        assertForeignCategoryUntouched("POST", "/archive", null);
    }

    @Test
    void unarchiveCategory() {
        jdbc.sql("update categories set archived_at = now() where id = :id")
                .param("id", ledger.categoryOf(anaWorkspace, "EXPENSE", "Pets"))
                .update();

        assertForeignCategoryUntouched("POST", "/unarchive", null);
    }

    @Test
    void newCategoryUnderForeignParent() {
        UUID anaCategory = ledger.categoryOf(anaWorkspace, "EXPENSE", "Mercado");
        String body = "{\"name\":\"Feira\",\"kind\":\"EXPENSE\",\"parentId\":\"%s\"}".formatted(anaCategory);

        var result = mvc.post()
                .uri("/v1/workspaces/" + biaWorkspace + "/categories")
                .with(TestUsers.signedIn(biaId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(biaId, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();

        assertThat(result).hasStatus(422).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("category.parent-invalid");
    }

    /** Bia names Ana's category under her own family: it is not found and nothing of it changes. */
    private void assertForeignCategoryUntouched(String method, String suffix, String body) {
        UUID anaCategory = ledger.categoryOf(anaWorkspace, "EXPENSE", "Pets");
        String before = categoryRow(anaCategory);
        var request = method.equals("PATCH") ? mvc.patch() : mvc.post();
        request.uri("/v1/workspaces/" + biaWorkspace + "/categories/" + anaCategory + suffix)
                .with(TestUsers.signedIn(biaId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(biaId, null)));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }

        var result = request.exchange();

        assertThat(result).hasStatus(404).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("category.not-found");
        assertThat(categoryRow(anaCategory)).isEqualTo(before);
    }

    private String categoryRow(UUID categoryId) {
        return jdbc.sql("select cast(c as text) from categories c where id = :id")
                .param("id", categoryId)
                .query(String.class)
                .single();
    }

    private MvcTestResult get(String route) {
        return mvc.get()
                .uri("/v1/workspaces/" + anaWorkspace + route)
                .with(TestUsers.signedIn(biaId, null))
                .exchange();
    }

    private static void assertNotFound(MvcTestResult result) {
        assertThat(result).hasStatus(404).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("workspace.not-found");
    }
}
