package br.com.liberdadefamilia.ledger.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * The race the optimistic lock exists for: another write lands after the edit passed its checks and before its
 * conditional update, so the update finds no row and the edit answers from a fresh read. The repository is a spy so
 * that the other write can run at exactly that point. The spy lives in this class's own context (a bean override is
 * part of the context cache key) and is reset after each test, so no other test sees it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransactionEditRaceIT {

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

    @MockitoSpyBean
    TransactionRepository transactions;

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

    /** Another tab saved in between: the answer carries the row as that save left it. */
    @Test
    void anotherEditBetweenChecksAndUpdate() {
        UUID id = expense();
        beforeTheUpdate(() -> jdbc.sql("""
                        update transactions
                        set version = version + 1, description = 'Outra aba'
                        where id = :id""").param("id", id).update());

        var result = patch(id, update(0));

        assertThat(result).hasStatus(409).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"code":"transaction.version-conflict","detail":"Este lançamento mudou desde que você abriu.",
                 "current":{"id":"%s","version":1,"description":"Outra aba",
                            "amount":{"amount":"12.34","currency":"BRL"}}}""".formatted(id));
        assertThat(storedVersion(id)).isOne();
    }

    /** Another tab deleted it in between: the edit says so instead of bringing it back. */
    @Test
    void deleteBetweenChecksAndUpdate() {
        UUID id = expense();
        beforeTheUpdate(() -> jdbc.sql("""
                        update transactions
                        set deleted_at = now(), version = version + 1
                        where id = :id""").param("id", id).update());

        var result = patch(id, update(0));

        assertThat(result).hasStatus(409).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"code":"transaction.deleted","detail":"Este lançamento foi excluído."}""");
        assertThat(jdbc.sql("select deleted_at is not null from transactions where id = :id")
                        .param("id", id)
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    /**
     * Runs the other write right before the repository's conditional update, then the update itself. The write runs
     * on its own thread, so on its own connection and committed: the edit is in a transaction that the 409 rolls back,
     * and the other tab's write is not part of it.
     */
    private void beforeTheUpdate(Runnable otherWrite) {
        doAnswer(invocation -> {
                    CompletableFuture.runAsync(otherWrite).join();
                    return invocation.callRealMethod();
                })
                .when(transactions)
                .update(any(), any(), any(), any(), any(), any());
    }

    private long storedVersion(UUID id) {
        return jdbc.sql("select version from transactions where id = :id")
                .param("id", id)
                .query(Long.class)
                .single();
    }

    /** Ana's 12.34 BRL market expense from the wallet on 2026-11-02, created through the API. */
    private UUID expense() {
        UUID id = TestIds.v7();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id.toString());
        body.put("kind", "EXPENSE");
        body.put("amount", Map.of("amount", "12.34", "currency", "BRL"));
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

    /** The full replacement that keeps the expense as it is, at the given version. */
    private Map<String, Object> update(long version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        body.put("kind", "EXPENSE");
        body.put("amount", Map.of("amount", "12.34", "currency", "BRL"));
        body.put("occurredOn", "2026-11-02");
        body.put("accountId", walletId.toString());
        body.put("categoryId", mercadoId.toString());
        return body;
    }

    private MvcTestResult patch(UUID id, Map<String, Object> body) {
        return mvc.patch()
                .uri("/v1/workspaces/" + workspaceId + "/transactions/" + id)
                .with(TestUsers.signedIn(anaId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(anaId, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))
                .exchange();
    }
}
