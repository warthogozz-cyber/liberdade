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

    private UUID anaWorkspace;
    private UUID biaId;
    private UUID biaWorkspace;

    @BeforeEach
    void families() {
        UUID anaId = users.create("Ana Teste", "ana@example.com");
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
