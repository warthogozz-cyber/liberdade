package br.com.liberdadefamilia.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.ledger.AccountChanged;
import br.com.liberdadefamilia.ledger.LedgerFigures;
import br.com.liberdadefamilia.ledger.TransactionChanged;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A goal that follows an account is reached when the ledger moves its balance past the target: after the ledger's
 * transaction commits, in a transaction of its own, and again on the next read when that failed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import({TestcontainersConfiguration.class, GoalAchievementIT.Config.class})
class GoalAchievementIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

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

    @Autowired
    TestClock clock;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    PlatformTransactionManager transactions;

    @MockitoSpyBean
    LedgerFigures figures;

    private UUID ana;
    private UUID ws;
    private UUID reserva;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-20T12:00:00Z"));
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        reserva = jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance)
                        values (:ws, 'Reserva', 'SAVINGS', 'BRL', 0)
                        returning id""").param("ws", ws).query(UUID.class).single();
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void transactionOnLinkedAccountAchieves() {
        UUID goal = linkedGoal("1000.00");
        assertThat(achievedAt(goal)).isNull();

        assertThat(income(reserva, "999.99")).hasStatus(201);
        assertThat(achievedAt(goal)).isNull();

        assertThat(income(reserva, "0.01")).hasStatus(201);
        assertThat(achievedAt(goal)).isEqualTo(Instant.parse("2026-11-20T12:00:00Z"));
        assertThat(versionOf(goal)).isZero();
    }

    @Test
    void transferIntoLinkedAccountAchieves() {
        UUID goal = linkedGoal("500.00");

        UUID id = TestIds.v7();
        var transfer = transaction(id, """
                {"id":"%s","kind":"TRANSFER","amount":{"amount":"500.00","currency":"BRL"},
                 "occurredOn":"2026-11-20","accountId":"%s","toAccountId":"%s"}""".formatted(id, ledger.walletOf(ws), reserva));

        assertThat(transfer).hasStatus(201);
        assertThat(achievedAt(goal)).isNotNull();
    }

    @Test
    void initialBalanceChangeAchieves() {
        UUID goal = linkedGoal("1000.00");

        var patched = send(HttpMethod.PATCH, "/accounts/" + reserva, """
                {"balance":{"amount":"1200.00","currency":"BRL"}}""");

        assertThat(patched).hasStatusOk();
        assertThat(achievedAt(goal)).isNotNull();
    }

    @Test
    void archivedGoalIsNotReached() {
        UUID goal = linkedGoal("100.00");
        assertThat(send(HttpMethod.POST, "/goals/" + goal + "/archive", null)).hasStatus(204);

        assertThat(income(reserva, "100.00")).hasStatus(201);

        assertThat(achievedAt(goal)).isNull();
    }

    @Test
    void listenerFailureDoesNotBreakTheSave(CapturedOutput output) {
        UUID goal = linkedGoal("1000.00");
        doThrow(new IllegalStateException("Reserva secreta 1000.00"))
                .when(figures)
                .accountBalance(any(), any());

        assertThat(income(reserva, "1000.00")).hasStatus(201);

        assertThat(achievedAt(goal)).isNull();
        assertThat(output).contains("java.lang.IllegalStateException").doesNotContain("Reserva secreta");

        reset(figures);
        var list = send(HttpMethod.GET, "/goals", null);
        assertThat(list).hasStatusOk();
        assertThat(list).bodyJson().extractingPath("$.goals[0].achievedAt").isNotNull();
        assertThat(achievedAt(goal)).isNotNull();
    }

    @Test
    void duplicateEventsWriteAchievedAtOnce() {
        UUID goal = linkedGoal("1000.00");
        jdbc.sql("update accounts set initial_balance = 1000 where id = :id")
                .param("id", reserva)
                .update();
        TransactionTemplate template = new TransactionTemplate(transactions);

        template.executeWithoutResult(status -> {
            events.publishEvent(new AccountChanged(ws, reserva));
            events.publishEvent(new AccountChanged(ws, reserva));
            events.publishEvent(new TransactionChanged(ws, TestIds.v7(), reserva, reserva));
        });
        Instant first = achievedAt(goal);
        assertThat(first).isEqualTo(Instant.parse("2026-11-20T12:00:00Z"));

        clock.freeze(Instant.parse("2026-11-21T12:00:00Z"));
        template.executeWithoutResult(status -> events.publishEvent(new AccountChanged(ws, reserva)));

        assertThat(achievedAt(goal)).isEqualTo(first);
        assertThat(versionOf(goal)).isZero();
    }

    private UUID linkedGoal(String target) {
        var created = post("/goals", """
                {"name":"Reserva da casa","targetAmount":{"amount":"%s","currency":"BRL"},"icon":"home",
                 "linkedAccountId":"%s"}""".formatted(target, reserva));
        assertThat(created).hasStatus(201);
        try {
            String id = JsonPath.read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
            return UUID.fromString(id);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult income(UUID account, String amount) {
        UUID id = TestIds.v7();
        return transaction(id, """
                {"id":"%s","kind":"INCOME","amount":{"amount":"%s","currency":"BRL"},"occurredOn":"2026-11-20",
                 "accountId":"%s","categoryId":"%s"}""".formatted(id, amount, account, ledger.categoryOf(ws, "INCOME", "Salário")));
    }

    /** A new transaction; its id is also the idempotency key, as the ledger requires. */
    private MvcTestResult transaction(UUID id, String body) {
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/transactions")
                .with(TestUsers.signedIn(ana, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)))
                .header("Idempotency-Key", id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private @Nullable Instant achievedAt(UUID goal) {
        return jdbc.sql("select achieved_at from goals where id = :id")
                .param("id", goal)
                .query((rs, n) -> Optional.ofNullable(rs.getTimestamp(1)).map(Timestamp::toInstant))
                .single()
                .orElse(null);
    }

    private long versionOf(UUID goal) {
        return jdbc.sql("select version from goals where id = :id")
                .param("id", goal)
                .query(Long.class)
                .single();
    }

    private MvcTestResult post(String path, String body) {
        return send(HttpMethod.POST, path, body);
    }

    private MvcTestResult send(HttpMethod method, String path, @Nullable String body) {
        var request = mvc.method(method).uri("/v1/workspaces/" + ws + path).with(TestUsers.signedIn(ana, null));
        if (method != HttpMethod.GET) {
            request.with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)));
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }
}
