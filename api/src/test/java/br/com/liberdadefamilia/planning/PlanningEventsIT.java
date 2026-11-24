package br.com.liberdadefamilia.planning;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Budgets and goals tell the rest of the app, by id only, when a write changed something. Today is 2026-11-20. */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import({TestcontainersConfiguration.class, PlanningEventsIT.Config.class})
class PlanningEventsIT {

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
    ApplicationEvents events;

    private UUID ana;
    private UUID ws;
    private UUID mercado;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-20T12:00:00Z"));
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        mercado = ledger.categoryOf(ws, "EXPENSE", "Mercado");
        events.clear();
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void budgetSetAndCopyPublish() {
        var created = send(HttpMethod.PUT, "/budgets/2026-11/" + mercado, limit("1200.00", null));
        assertThat(created).hasStatus(201);
        assertThat(budgetChanges()).containsExactly(new BudgetChanged(ws, YearMonth.of(2026, 11)));

        assertThat(send(HttpMethod.PUT, "/budgets/2026-11/" + mercado, limit("1500.00", 0L)))
                .hasStatusOk();
        assertThat(budgetChanges()).hasSize(2);

        // Nothing in October to copy: nothing changed, nothing published.
        assertThat(send(HttpMethod.POST, "/budgets/2026-11/copy-previous", null))
                .bodyJson()
                .extractingPath("$.copied")
                .isEqualTo(0);
        assertThat(budgetChanges()).hasSize(2);

        assertThat(send(HttpMethod.POST, "/budgets/2026-12/copy-previous", null))
                .bodyJson()
                .extractingPath("$.copied")
                .isEqualTo(1);
        assertThat(budgetChanges()).hasSize(3).last().isEqualTo(new BudgetChanged(ws, YearMonth.of(2026, 12)));

        assertThat(send(HttpMethod.DELETE, "/budgets/2026-11/" + mercado, null)).hasStatus(204);
        assertThat(budgetChanges()).hasSize(4);

        // Deleting a limit that is not there changes nothing.
        assertThat(send(HttpMethod.DELETE, "/budgets/2026-11/" + mercado, null)).hasStatus(204);
        assertThat(budgetChanges()).hasSize(4);
    }

    @Test
    void goalWritesPublish() {
        var created = send(HttpMethod.POST, "/goals", goal(null));
        assertThat(created).hasStatus(201);
        UUID goal = UUID.fromString(JsonPath.read(body(created), "$.id"));

        assertThat(send(HttpMethod.PATCH, "/goals/" + goal, "{\"name\":\"Viagem à praia\",\"version\":0}"))
                .hasStatusOk();
        assertThat(send(HttpMethod.POST, "/goals/" + goal + "/contributions", """
                        {"amount":{"amount":"50.00","currency":"BRL"},"occurredOn":"2026-11-20"}"""))
                .hasStatus(201);

        assertThat(goalChanges())
                .containsExactly(new GoalChanged(ws, goal), new GoalChanged(ws, goal), new GoalChanged(ws, goal));
    }

    @Test
    void achievementPublishesOnce() {
        UUID reserva = jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance)
                        values (:ws, 'Reserva', 'SAVINGS', 'BRL', 0) returning id""").param("ws", ws).query(UUID.class).single();
        UUID goal = UUID.fromString(JsonPath.read(body(send(HttpMethod.POST, "/goals", goal(reserva))), "$.id"));
        events.clear();

        assertThat(income(reserva, "999.00")).hasStatus(201);
        assertThat(goalChanges()).isEmpty();

        assertThat(income(reserva, "1.00")).hasStatus(201);
        assertThat(goalChanges()).containsExactly(new GoalChanged(ws, goal));

        assertThat(income(reserva, "5.00")).hasStatus(201);
        assertThat(goalChanges()).hasSize(1);
    }

    private List<BudgetChanged> budgetChanges() {
        return events.stream(BudgetChanged.class).toList();
    }

    private List<GoalChanged> goalChanges() {
        return events.stream(GoalChanged.class).toList();
    }

    private static String limit(String amount, @Nullable Long version) {
        return "{\"limitAmount\":{\"amount\":\"%s\",\"currency\":\"BRL\"},\"version\":%s}".formatted(amount, version);
    }

    private static String goal(@Nullable UUID linked) {
        return """
                {"name":"Viagem","targetAmount":{"amount":"1000.00","currency":"BRL"},"icon":"travel",
                 "linkedAccountId":%s}""".formatted(linked == null ? "null" : "\"" + linked + "\"");
    }

    private MvcTestResult income(UUID account, String amount) {
        UUID id = TestIds.v7();
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/transactions")
                .with(TestUsers.signedIn(ana, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)))
                .header("Idempotency-Key", id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id":"%s","kind":"INCOME","amount":{"amount":"%s","currency":"BRL"},"occurredOn":"2026-11-20",
                         "accountId":"%s","categoryId":"%s"}""".formatted(id, amount, account, ledger.categoryOf(ws, "INCOME", "Salário")))
                .exchange();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
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
