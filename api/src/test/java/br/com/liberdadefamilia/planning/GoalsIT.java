package br.com.liberdadefamilia.planning;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The family's goals, what was put aside for them and the accounts they follow. Today is 2026-11-20. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, GoalsIT.Config.class})
class GoalsIT {

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
    TestClock clock;

    private UUID ana;
    private UUID ws;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-20T12:00:00Z"));
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void createAndList() {
        var created = create(ana, "Viagem", "12000.00", "2027-08", "travel", null);
        assertThat(created).hasStatus(201);
        assertThat(created).bodyJson().extractingPath("$.name").isEqualTo("Viagem");
        assertThat(created).bodyJson().extractingPath("$.version").isEqualTo(0);

        var list = send(ana, HttpMethod.GET, "/goals", null);
        assertThat(list).hasStatusOk();
        assertThat(list).bodyJson().extractingPath("$.goals.length()").isEqualTo(1);
        assertThat(list).bodyJson().extractingPath("$.goals[0].icon").isEqualTo("travel");
        assertThat(list)
                .bodyJson()
                .extractingPath("$.goals[0].targetAmount.amount")
                .isEqualTo("12000.00");
        assertThat(list)
                .bodyJson()
                .extractingPath("$.goals[0].targetAmount.currency")
                .isEqualTo("BRL");
        assertThat(list).bodyJson().extractingPath("$.goals[0].saved.amount").isEqualTo("0.00");
        assertThat(list).bodyJson().extractingPath("$.goals[0].percent").isEqualTo(0);
        assertThat(list).bodyJson().extractingPath("$.goals[0].dueOn").isEqualTo("2027-08");
        assertThat(list)
                .bodyJson()
                .extractingPath("$.goals[0].suggestedMonthly.amount")
                .isEqualTo("1333.34");
        assertThat(list).bodyJson().extractingPath("$.goals[0].linked").isEqualTo(false);
        assertThat(list).bodyJson().extractingPath("$.goals[0].achievedAt").isNull();
        assertThat(list).bodyJson().extractingPath("$.limitReached").isEqualTo(false);
    }

    @Test
    void contributionsAddUpAndSuggest() {
        UUID goal = idOf(create(ana, "Viagem", "12000.00", "2027-08", "travel", null));

        var contribution = contribute(ana, goal, "3000.00");
        assertThat(contribution).hasStatus(201);
        assertThat(contribution).bodyJson().extractingPath("$.amount.amount").isEqualTo("3000.00");
        assertThat(contribution).bodyJson().extractingPath("$.occurredOn").isEqualTo("2026-11-20");

        var list = send(ana, HttpMethod.GET, "/goals", null);
        assertThat(list).bodyJson().extractingPath("$.goals[0].saved.amount").isEqualTo("3000.00");
        assertThat(list).bodyJson().extractingPath("$.goals[0].percent").isEqualTo(25);
        assertThat(list)
                .bodyJson()
                .extractingPath("$.goals[0].suggestedMonthly.amount")
                .isEqualTo("1000.00");

        var history = send(ana, HttpMethod.GET, "/goals/" + goal + "/contributions", null);
        assertThat(history).hasStatusOk();
        assertThat(history).bodyJson().extractingPath("$.length()").isEqualTo(1);
    }

    @Test
    void withdrawalCannotGoNegative() {
        UUID goal = idOf(create(ana, "Viagem", "12000.00", null, "travel", null));
        assertThat(contribute(ana, goal, "3000.00")).hasStatus(201);

        var tooMuch = contribute(ana, goal, "-3500.00");
        assertThat(tooMuch).hasStatus(422);
        assertThat(tooMuch).bodyJson().extractingPath("$.code").isEqualTo("goal.negative-balance");
        assertThat(tooMuch).bodyJson().extractingPath("$.detail").isEqualTo("O guardado não pode ficar negativo.");

        assertThat(contribute(ana, goal, "-500.00")).hasStatus(201);
        var list = send(ana, HttpMethod.GET, "/goals", null);
        assertThat(list).bodyJson().extractingPath("$.goals[0].saved.amount").isEqualTo("2500.00");
        assertThat(list)
                .bodyJson()
                .extractingPath("$.goals[0].suggestedMonthly")
                .isNull();
    }

    @Test
    void zeroContributionInvalid() {
        UUID goal = idOf(create(ana, "Viagem", "12000.00", null, "travel", null));

        var zero = contribute(ana, goal, "0.00");

        assertThat(zero).hasStatus(400);
        assertThat(zero).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
    }

    @Test
    void reachingTargetSetsAchievedAt() {
        UUID goal = idOf(create(ana, "Bicicleta", "1000.00", "2027-02", "other", null));

        assertThat(contribute(ana, goal, "1000.00")).hasStatus(201);
        assertThat(achievedAt(goal)).isNotNull();

        var list = send(ana, HttpMethod.GET, "/goals", null);
        assertThat(list).bodyJson().extractingPath("$.goals[0].achievedAt").isNotNull();
        assertThat(list).bodyJson().extractingPath("$.goals[0].percent").isEqualTo(100);
        assertThat(list)
                .bodyJson()
                .extractingPath("$.goals[0].suggestedMonthly")
                .isNull();

        assertThat(contribute(ana, goal, "-200.00")).hasStatus(201);
        var after = send(ana, HttpMethod.GET, "/goals", null);
        assertThat(after).bodyJson().extractingPath("$.goals[0].achievedAt").isNotNull();
        assertThat(after).bodyJson().extractingPath("$.goals[0].percent").isEqualTo(80);
    }

    @Test
    void activeFirstThenAchievedNewestFirst() {
        UUID first = idOf(create(ana, "Primeira", "100.00", null, "gift", null));
        UUID second = idOf(create(ana, "Segunda", "100.00", null, "gift", null));
        UUID third = idOf(create(ana, "Terceira", "100.00", null, "gift", null));
        contribute(ana, first, "100.00");
        clock.freeze(Instant.parse("2026-11-20T13:00:00Z"));
        contribute(ana, third, "100.00");

        var list = send(ana, HttpMethod.GET, "/goals", null);

        assertThat(list).bodyJson().extractingPath("$.goals[0].id").isEqualTo(second.toString());
        assertThat(list).bodyJson().extractingPath("$.goals[1].id").isEqualTo(third.toString());
        assertThat(list).bodyJson().extractingPath("$.goals[2].id").isEqualTo(first.toString());
    }

    @Test
    void loweringTargetAchieves() {
        UUID goal = idOf(create(ana, "Viagem", "12000.00", null, "travel", null));
        contribute(ana, goal, "3000.00");

        var patched = send(ana, HttpMethod.PATCH, "/goals/" + goal, """
                {"targetAmount":{"amount":"3000.00","currency":"BRL"},"version":0}""");

        assertThat(patched).hasStatusOk();
        assertThat(patched).bodyJson().extractingPath("$.achievedAt").isNotNull();
        assertThat(patched).bodyJson().extractingPath("$.version").isEqualTo(1);
        assertThat(patched).bodyJson().extractingPath("$.percent").isEqualTo(100);
        assertThat(versionOf(goal)).isEqualTo(1L);
    }

    @Test
    void patchChangesNameIconAndDueAndChecksVersion() {
        UUID goal = idOf(create(ana, "Viagem", "12000.00", null, "travel", null));

        var patched = send(ana, HttpMethod.PATCH, "/goals/" + goal, """
                {"name":"  Viagem à praia ","icon":"party","dueOn":"2027-11","version":0}""");
        assertThat(patched).hasStatusOk();
        assertThat(patched).bodyJson().extractingPath("$.name").isEqualTo("Viagem à praia");
        assertThat(patched).bodyJson().extractingPath("$.icon").isEqualTo("party");
        assertThat(patched).bodyJson().extractingPath("$.dueOn").isEqualTo("2027-11");

        var stale = send(ana, HttpMethod.PATCH, "/goals/" + goal, """
                {"name":"Outra","version":0}""");
        assertThat(stale).hasStatus(409);
        assertThat(stale).bodyJson().extractingPath("$.code").isEqualTo("goal.version-conflict");

        var past = send(ana, HttpMethod.PATCH, "/goals/" + goal, """
                {"dueOn":"2026-10","version":1}""");
        assertThat(past).hasStatus(422);
        assertThat(past).bodyJson().extractingPath("$.code").isEqualTo("goal.due-in-past");
    }

    @Test
    void linkedGoalFollowsBalanceAndRejectsContributions() {
        UUID reserva = account("Reserva", "BRL", "4000.00", false);
        UUID goal = idOf(create(ana, "Reserva da casa", "10000.00", null, "home", reserva));

        var list = send(ana, HttpMethod.GET, "/goals", null);
        assertThat(list).bodyJson().extractingPath("$.goals[0].saved.amount").isEqualTo("4000.00");
        assertThat(list).bodyJson().extractingPath("$.goals[0].percent").isEqualTo(40);
        assertThat(list).bodyJson().extractingPath("$.goals[0].linked").isEqualTo(true);
        assertThat(list).bodyJson().extractingPath("$.goals[0].linkedAccountId").isEqualTo(reserva.toString());

        var contribution = contribute(ana, goal, "100.00");
        assertThat(contribution).hasStatus(422);
        assertThat(contribution).bodyJson().extractingPath("$.code").isEqualTo("goal.linked-account");
        assertThat(contribution)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Esta meta acompanha o saldo de uma conta.");
    }

    @Test
    void linkedGoalNeverShowsANegativeSaved() {
        UUID cartao = account("Cartão", "BRL", "-300.00", false);
        idOf(create(ana, "Quitar", "1000.00", null, "other", cartao));

        var list = send(ana, HttpMethod.GET, "/goals", null);

        assertThat(list).bodyJson().extractingPath("$.goals[0].saved.amount").isEqualTo("0.00");
        assertThat(list).bodyJson().extractingPath("$.goals[0].percent").isEqualTo(0);
    }

    @Test
    void linkedAccountMustMatch() {
        UUID archived = account("Antiga", "BRL", "0.00", true);
        UUID dollars = account("Dólares", "USD", "0.00", false);

        var invalid = create(ana, "Meta", "1000.00", null, "other", archived);
        assertThat(invalid).hasStatus(422);
        assertThat(invalid).bodyJson().extractingPath("$.code").isEqualTo("goal.linked-account-invalid");

        var missing = create(ana, "Meta", "1000.00", null, "other", UUID.randomUUID());
        assertThat(missing).hasStatus(422);
        assertThat(missing).bodyJson().extractingPath("$.code").isEqualTo("goal.linked-account-invalid");

        var currency = create(ana, "Meta", "1000.00", null, "other", dollars);
        assertThat(currency).hasStatus(422);
        assertThat(currency).bodyJson().extractingPath("$.code").isEqualTo("goal.currency");
    }

    @Test
    void targetMustBeInTheBaseCurrency() {
        var result = send(ana, HttpMethod.POST, "/goals", """
                {"name":"Meta","targetAmount":{"amount":"100.00","currency":"USD"},"icon":"other"}""");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("goal.currency");
    }

    @Test
    void pastDueRejected() {
        var result = create(ana, "Viagem", "12000.00", "2026-10", "travel", null);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("goal.due-in-past");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O prazo não pode ser um mês que já passou.");
        assertThat(create(ana, "Viagem", "12000.00", "2026-11", "travel", null)).hasStatus(201);
    }

    @Test
    void unknownIconInvalid() {
        var result = create(ana, "Foguete", "1000.00", null, "rocket", null);

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
    }

    @Test
    void archiveIsIdempotentAndAuditedOnce() {
        UUID goal = idOf(create(ana, "Viagem", "12000.00", null, "travel", null));

        assertThat(send(ana, HttpMethod.POST, "/goals/" + goal + "/archive", null))
                .hasStatus(204);
        assertThat(send(ana, HttpMethod.POST, "/goals/" + goal + "/archive", null))
                .hasStatus(204);

        assertThat(auditRows(goal)).isEqualTo(1);
        var list = send(ana, HttpMethod.GET, "/goals", null);
        assertThat(list).bodyJson().extractingPath("$.goals.length()").isEqualTo(0);
        var contribution = contribute(ana, goal, "100.00");
        assertThat(contribution).hasStatus(404);
        assertThat(contribution).bodyJson().extractingPath("$.code").isEqualTo("goal.not-found");
        assertThat(send(ana, HttpMethod.POST, "/goals/" + UUID.randomUUID() + "/archive", null))
                .hasStatus(404);
    }

    @Test
    void youthNeverSeesLinkedAccount() {
        UUID leo = users.create("Leo Teste", "leo@example.com");
        workspaces.addMember(ws, leo, Role.YOUTH);
        UUID reserva = account("Reserva", "BRL", "100.00", false);
        UUID goal = idOf(create(ana, "Reserva", "1000.00", null, "savings", reserva));

        var list = send(leo, HttpMethod.GET, "/goals", null);
        assertThat(list).hasStatusOk();
        assertThat(list).bodyJson().extractingPath("$.goals[0].linked").isEqualTo(true);
        assertThat(list).bodyJson().extractingPath("$.goals[0].linkedAccountId").isNull();
        assertThat(list).bodyJson().extractingPath("$.limitReached").isEqualTo(false);
        assertThat(send(leo, HttpMethod.GET, "/goals/" + goal + "/contributions", null))
                .hasStatusOk();

        var write = create(leo, "Minha", "100.00", null, "gift", null);
        assertThat(write).hasStatus(403);
        assertThat(write).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
    }

    @Test
    void viewerReadsOnly() {
        UUID vera = users.create("Vera Teste", "vera@example.com");
        workspaces.addMember(ws, vera, Role.VIEWER);
        UUID goal = idOf(create(ana, "Viagem", "12000.00", null, "travel", null));

        assertThat(send(vera, HttpMethod.GET, "/goals", null)).hasStatusOk();
        assertThat(send(vera, HttpMethod.GET, "/goals/" + goal + "/contributions", null))
                .hasStatusOk();
        assertThat(contribute(vera, goal, "100.00")).hasStatus(403);
        assertThat(send(vera, HttpMethod.POST, "/goals/" + goal + "/archive", null))
                .hasStatus(403);
    }

    @Test
    void otherFamilyNotFound() {
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID otherWs = workspaces.createFor(bia);
        UUID theirs =
                idOf(sendTo(otherWs, bia, HttpMethod.POST, "/goals", body("Delas", "100.00", null, "gift", null)));

        for (MvcTestResult result : List.of(
                contribute(ana, theirs, "10.00"),
                send(ana, HttpMethod.GET, "/goals/" + theirs + "/contributions", null),
                send(ana, HttpMethod.POST, "/goals/" + theirs + "/archive", null),
                send(ana, HttpMethod.PATCH, "/goals/" + theirs, "{\"name\":\"Minha\",\"version\":0}"))) {
            assertThat(result).hasStatus(404);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("goal.not-found");
        }
        assertThat(send(ana, HttpMethod.GET, "/goals", null))
                .bodyJson()
                .extractingPath("$.goals.length()")
                .isEqualTo(0);

        UUID theirAccount = jdbc.sql("select id from accounts where workspace_id = :ws limit 1")
                .param("ws", otherWs)
                .query(UUID.class)
                .single();
        var linked = create(ana, "Meta", "100.00", null, "gift", theirAccount);
        assertThat(linked).hasStatus(422);
        assertThat(linked).bodyJson().extractingPath("$.code").isEqualTo("goal.linked-account-invalid");
    }

    @Test
    void moduleOff() {
        UUID goal = idOf(create(ana, "Viagem", "12000.00", null, "travel", null));
        assertThat(send(ana, HttpMethod.PUT, "/modules/planning", "{\"enabled\":false}"))
                .hasStatusOk();

        for (MvcTestResult result : List.of(
                send(ana, HttpMethod.GET, "/goals", null),
                create(ana, "Outra", "100.00", null, "gift", null),
                contribute(ana, goal, "10.00"))) {
            assertThat(result).hasStatus(403);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("module.disabled");
        }
    }

    private UUID account(String name, String currency, String initial, boolean archived) {
        return jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance, archived_at)
                        values (:ws, :name, 'SAVINGS', :currency, cast(:initial as numeric),
                                case when :archived then now() end)
                        returning id""")
                .param("ws", ws)
                .param("name", name)
                .param("currency", currency)
                .param("initial", initial)
                .param("archived", archived)
                .query(UUID.class)
                .single();
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

    private int auditRows(UUID goal) {
        return jdbc.sql("select count(*) from audit_events where action = 'goal.archived' and target_id = :id")
                .param("id", goal)
                .query(Integer.class)
                .single();
    }

    private MvcTestResult create(
            UUID caller, String name, String target, @Nullable String due, String icon, @Nullable UUID linked) {
        return send(caller, HttpMethod.POST, "/goals", body(name, target, due, icon, linked));
    }

    private static String body(String name, String target, @Nullable String due, String icon, @Nullable UUID linked) {
        return """
                {"name":"%s","targetAmount":{"amount":"%s","currency":"BRL"},"dueOn":%s,"icon":"%s",
                 "linkedAccountId":%s}""".formatted(
                        name,
                        target,
                        due == null ? "null" : "\"" + due + "\"",
                        icon,
                        linked == null ? "null" : "\"" + linked + "\"");
    }

    private MvcTestResult contribute(UUID caller, UUID goal, String amount) {
        return send(caller, HttpMethod.POST, "/goals/" + goal + "/contributions", """
                {"amount":{"amount":"%s","currency":"BRL"},"occurredOn":"2026-11-20"}""".formatted(amount));
    }

    private static UUID idOf(MvcTestResult result) {
        assertThat(result).hasStatus(201);
        try {
            String id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
            return UUID.fromString(id);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult send(UUID caller, HttpMethod method, String path, @Nullable String body) {
        return sendTo(ws, caller, method, path, body);
    }

    private MvcTestResult sendTo(UUID workspace, UUID caller, HttpMethod method, String path, @Nullable String body) {
        var request =
                mvc.method(method).uri("/v1/workspaces/" + workspace + path).with(TestUsers.signedIn(caller, null));
        if (method != HttpMethod.GET) {
            request.with(CsrfSession.start(mvc, TestUsers.signedIn(caller, null)));
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }
}
