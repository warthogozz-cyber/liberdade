package br.com.liberdadefamilia.planning;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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

/**
 * The balance projection (D13). Today is 2026-11-20. The family example: R$ 5.000 in the accounts up to today, a
 * salary of R$ 6.000 from 05/12 and a rent of R$ 2.000 from 25/11 (both start after today, so nothing is overdue), and
 * spending without a rule of R$ 3.000, R$ 3.300 and R$ 3.600 in August, September and October.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ProjectionIT.Config.class})
class ProjectionIT {

    private static final List<String> EXAMPLE = List.of("1900.00", "2600.00", "3300.00", "4000.00");

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

    private UUID ana;
    private UUID anaMembership;
    private UUID ws;
    private UUID wallet;
    private UUID mercado;
    private UUID salario;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-20T12:00:00Z"));
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        anaMembership = workspaces.membershipOf(ws, ana);
        wallet = ledger.walletOf(ws);
        mercado = ledger.categoryOf(ws, "EXPENSE", "Mercado");
        salario = ledger.categoryOf(ws, "INCOME", "Salário");
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void specExample() {
        example();

        var result = get(ana);

        assertThat(result).hasStatusOk();
        assertThat(balances(result)).containsExactlyElementsOf(EXAMPLE);
        assertThat(result).bodyJson().extractingPath("$.today").isEqualTo("2026-11-20");
        assertThat(result).bodyJson().extractingPath("$.balanceToday.amount").isEqualTo("5000.00");
        assertThat(result).bodyJson().extractingPath("$.balanceToday.currency").isEqualTo("BRL");
        assertThat(result).bodyJson().extractingPath("$.hasHistory").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.averageSpending.amount").isEqualTo("3300.00");
        assertThat(result).bodyJson().extractingPath("$.points.length()").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.points[0].monthEnd").isEqualTo("2026-11-30");
        assertThat(result).bodyJson().extractingPath("$.points[3].monthEnd").isEqualTo("2027-02-28");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.points[0].scheduled.amount")
                .isEqualTo("-2000.00");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.points[0].estimatedSpending.amount")
                .isEqualTo("1100.00");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.points[1].scheduled.amount")
                .isEqualTo("2000.00");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.points[1].estimatedSpending.amount")
                .isEqualTo("4400.00");
    }

    @Test
    void projectionWithoutHistory() {
        setWalletBalance("5000.00");
        rules();

        var result = get(ana);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.hasHistory").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.averageSpending").isNull();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.points[3].estimatedSpending.amount")
                .isEqualTo("0.00");
        assertThat(balances(result)).containsExactly("3000.00", "7000.00", "11000.00", "15000.00");
    }

    @Test
    void skippedPlannedNeverCounts() {
        UUID rent = example();
        UUID december = plannedOn(rent, "2026-12-25");

        assertThat(send(ana, HttpMethod.POST, "/transactions/" + december + "/skip", "{\"version\":0}"))
                .hasStatus(204);

        assertThat(balances(get(ana))).containsExactly("1900.00", "4600.00", "5300.00", "6000.00");
    }

    @Test
    void confirmedEarlyCountsOnce() {
        UUID rent = example();
        UUID november = plannedOn(rent, "2026-11-25");

        assertThat(send(ana, HttpMethod.POST, "/transactions/" + november + "/confirm", """
                        {"version":0,"occurredOn":"2026-11-20"}"""))
                .hasStatusOk();

        var result = get(ana);
        assertThat(result).bodyJson().extractingPath("$.balanceToday.amount").isEqualTo("3000.00");
        assertThat(balances(result)).containsExactlyElementsOf(EXAMPLE);
    }

    @Test
    void adjustedPlannedCountsOnce() {
        UUID rent = example();
        UUID december = plannedOn(rent, "2026-12-25");

        assertThat(send(ana, HttpMethod.POST, "/transactions/" + december + "/confirm", """
                        {"version":0,"amount":{"amount":"2100.00","currency":"BRL"},"occurredOn":"2026-11-22"}"""))
                .hasStatusOk();

        var result = get(ana);
        assertThat(result).bodyJson().extractingPath("$.balanceToday.amount").isEqualTo("5000.00");
        assertThat(balances(result)).containsExactly("-200.00", "2500.00", "3200.00", "3900.00");
    }

    @Test
    void beyondHorizonIsComputed() {
        example();
        int deleted = jdbc.sql("""
                        delete from transactions
                        where workspace_id = :ws
                          and recurring_rule_id is not null
                          and scheduled_on between date '2026-12-01' and date '2026-12-31'""").param("ws", ws).update();
        assertThat(deleted).isEqualTo(2);

        assertThat(balances(get(ana))).containsExactlyElementsOf(EXAMPLE);
    }

    @Test
    void archivedAccountRuleIgnored() {
        example();
        UUID old = account("Conta antiga");
        createRule("EXPENSE", "500.00", old, null, mercado, "2026-11-28");
        assertThat(send(ana, HttpMethod.POST, "/accounts/" + old + "/archive", null))
                .hasStatusOk();
        assertThat(plannedCount(old)).isEqualTo(2);

        assertThat(balances(get(ana))).containsExactlyElementsOf(EXAMPLE);
    }

    @Test
    void transferIsNeutral() {
        example();
        UUID savings = account("Poupança");
        createRule("TRANSFER", "1000.00", wallet, savings, null, "2026-11-28");

        var result = get(ana);
        assertThat(result).bodyJson().extractingPath("$.balanceToday.amount").isEqualTo("5000.00");
        assertThat(balances(result)).containsExactlyElementsOf(EXAMPLE);
    }

    /**
     * A transfer to an account left out of the balance takes the money out of it: R$ 500 a month leave the counted
     * accounts, both the planned rows the job made and the occurrences computed after its horizon.
     */
    @Test
    void transferToArchivedAccountLeavesTheBalance() {
        example();
        UUID old = account("Conta antiga");
        createRule("TRANSFER", "500.00", wallet, old, null, "2026-11-28");
        assertThat(send(ana, HttpMethod.POST, "/accounts/" + old + "/archive", null))
                .hasStatusOk();

        assertThat(balances(get(ana))).containsExactly("1400.00", "1600.00", "1800.00", "2000.00");
    }

    @Test
    void viewerReads() {
        example();
        UUID vera = users.create("Vera Teste", "vera@example.com");
        workspaces.addMember(ws, vera, Role.VIEWER);

        var result = get(vera);

        assertThat(result).hasStatusOk();
        assertThat(balances(result)).containsExactlyElementsOf(EXAMPLE);
    }

    @Test
    void youthIsForbidden() {
        UUID leo = users.create("Leo Teste", "leo@example.com");
        workspaces.addMember(ws, leo, Role.YOUTH);

        var result = get(leo);

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
    }

    @Test
    void moduleOff() {
        assertThat(send(ana, HttpMethod.PUT, "/modules/planning", "{\"enabled\":false}"))
                .hasStatusOk();

        var result = get(ana);

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("module.disabled");
    }

    /** The whole family example; returns the rent rule. */
    private UUID example() {
        // 14.900 at the start, less the 9.900 spent from August to October: 5.000 up to today.
        setWalletBalance("14900.00");
        ledger.expense(ws, anaMembership, mercado, "3000.00", LocalDate.of(2026, 8, 10));
        ledger.expense(ws, anaMembership, mercado, "3300.00", LocalDate.of(2026, 9, 10));
        ledger.expense(ws, anaMembership, mercado, "3600.00", LocalDate.of(2026, 10, 10));
        return rules();
    }

    /** The salary and rent rules, with the planned rows the generator makes up to 31/12; returns the rent rule. */
    private UUID rules() {
        createRule("INCOME", "6000.00", wallet, null, salario, "2026-12-05");
        return createRule("EXPENSE", "2000.00", wallet, null, mercado, "2026-11-25");
    }

    private UUID createRule(String kind, String amount, UUID account, UUID toAccount, UUID category, String startOn) {
        String body = """
                {"kind":"%s","amount":{"amount":"%s","currency":"BRL"},"accountId":"%s","toAccountId":%s,
                 "categoryId":%s,"frequency":"MONTHLY","interval":1,"startOn":"%s","endOn":null}""".formatted(kind, amount, account, quoted(toAccount), quoted(category), startOn);
        var result = send(ana, HttpMethod.POST, "/recurring-rules", body);
        assertThat(result).hasStatus(201);
        return UUID.fromString(JsonPath.read(body(result), "$.id"));
    }

    private static String quoted(UUID id) {
        return id == null ? "null" : "\"" + id + "\"";
    }

    private void setWalletBalance(String amount) {
        jdbc.sql("update accounts set initial_balance = :amount::numeric where id = :id")
                .param("amount", amount)
                .param("id", wallet)
                .update();
    }

    private UUID account(String name) {
        return jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance)
                        values (:ws, :name, 'SAVINGS', 'BRL', 0)
                        returning id""")
                .param("ws", ws)
                .param("name", name)
                .query(UUID.class)
                .single();
    }

    private UUID plannedOn(UUID rule, String date) {
        return jdbc.sql("""
                        select id from transactions
                        where recurring_rule_id = :rule and scheduled_on = cast(:date as date)""")
                .param("rule", rule)
                .param("date", date)
                .query(UUID.class)
                .single();
    }

    private int plannedCount(UUID account) {
        return jdbc.sql("""
                        select count(*) from transactions
                        where account_id = :account and status = 'PLANNED' and deleted_at is null""").param("account", account).query(Integer.class).single();
    }

    private static List<String> balances(MvcTestResult result) {
        return JsonPath.read(body(result), "$.points[*].balance.amount");
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult get(UUID caller) {
        return send(caller, HttpMethod.GET, "/projection", null);
    }

    private MvcTestResult send(UUID caller, HttpMethod method, String path, String body) {
        var request = mvc.method(method).uri("/v1/workspaces/" + ws + path).with(TestUsers.signedIn(caller, null));
        if (method != HttpMethod.GET) {
            request.with(CsrfSession.start(mvc, TestUsers.signedIn(caller, null)));
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }
}
