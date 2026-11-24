package br.com.liberdadefamilia.planning;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
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

/** The emergency reserve and the months of cost it covers. Today is 2026-11-20. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ReserveIT.Config.class})
class ReserveIT {

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
    private UUID mercado;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-20T12:00:00Z"));
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        anaMembership = workspaces.membershipOf(ws, ana);
        mercado = ledger.categoryOf(ws, "EXPENSE", "Mercado");
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void prdExample() {
        reserveAccount("18000.00");
        threeMonths();

        var result = get(ana);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.months").isEqualTo(4.0);
        assertThat(result).bodyJson().extractingPath("$.missing.amount").isEqualTo("9000.00");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.averageMonthlyCost.amount")
                .isEqualTo("4500.00");
        assertThat(result).bodyJson().extractingPath("$.costSource").isEqualTo("HISTORY");
        assertThat(result).bodyJson().extractingPath("$.balance.amount").isEqualTo("18000.00");
        assertThat(result).bodyJson().extractingPath("$.hasReserveAccounts").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.targetMonths").isEqualTo(6);
        assertThat(result).bodyJson().extractingPath("$.manualMonthlyCost").isNull();
    }

    @Test
    void currentMonthDoesNotCount() {
        reserveAccount("18000.00");
        threeMonths();
        ledger.expense(ws, anaMembership, mercado, "10000.00", LocalDate.of(2026, 11, 3));

        var result = get(ana);

        assertThat(result).bodyJson().extractingPath("$.months").isEqualTo(4.0);
        assertThat(result).bodyJson().extractingPath("$.missing.amount").isEqualTo("9000.00");
        assertThat(result).bodyJson().extractingPath("$.costSource").isEqualTo("HISTORY");
    }

    @Test
    void reserveWithoutHistoryAsksForCost() {
        reserveAccount("18000.00");
        ledger.expense(ws, anaMembership, mercado, "100.00", LocalDate.of(2026, 10, 5));

        var result = get(ana);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.costSource").isEqualTo("NONE");
        assertThat(result).bodyJson().extractingPath("$.months").isNull();
        assertThat(result).bodyJson().extractingPath("$.missing").isNull();
        assertThat(result).bodyJson().extractingPath("$.averageMonthlyCost").isNull();
    }

    @Test
    void manualCostIsUsed() {
        reserveAccount("18000.00");
        ledger.expense(ws, anaMembership, mercado, "100.00", LocalDate.of(2026, 10, 5));
        assertThat(patch(ana, "{\"manualMonthlyCost\":{\"amount\":\"4500\",\"currency\":\"BRL\"}}"))
                .hasStatusOk();

        var result = get(ana);

        assertThat(result).bodyJson().extractingPath("$.costSource").isEqualTo("MANUAL");
        assertThat(result).bodyJson().extractingPath("$.months").isEqualTo(4.0);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.manualMonthlyCost.amount")
                .isEqualTo("4500.00");
        assertThat(result).bodyJson().extractingPath("$.missing.amount").isEqualTo("9000.00");
    }

    @Test
    void zeroSpendingIsShortHistory() {
        reserveAccount("18000.00");
        ledger.expense(ws, anaMembership, mercado, "50.00", LocalDate.of(2026, 5, 5));

        var result = get(ana);

        assertThat(result).bodyJson().extractingPath("$.costSource").isEqualTo("NONE");
        assertThat(result).bodyJson().extractingPath("$.months").isNull();
        assertThat(result).bodyJson().extractingPath("$.missing").isNull();
    }

    @Test
    void noReserveAccounts() {
        var result = get(ana);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.hasReserveAccounts").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.balance.amount").isEqualTo("0.00");
    }

    @Test
    void patchTargetAndCost() {
        reserveAccount("18000.00");
        threeMonths();

        var target = patch(ana, "{\"targetMonths\":12}");
        assertThat(target).hasStatusOk();
        assertThat(target).bodyJson().extractingPath("$.targetMonths").isEqualTo(12);
        assertThat(target).bodyJson().extractingPath("$.missing.amount").isEqualTo("36000.00");

        var cost = patch(ana, "{\"manualMonthlyCost\":{\"amount\":\"900.00\",\"currency\":\"BRL\"}}");
        assertThat(cost).hasStatusOk();
        assertThat(cost).bodyJson().extractingPath("$.manualMonthlyCost.amount").isEqualTo("900.00");
        assertThat(cost).bodyJson().extractingPath("$.targetMonths").isEqualTo(12);

        var kept = patch(ana, "{\"targetMonths\":8}");
        assertThat(kept).bodyJson().extractingPath("$.manualMonthlyCost.amount").isEqualTo("900.00");

        var cleared = patch(ana, "{\"manualMonthlyCost\":null}");
        assertThat(cleared).hasStatusOk();
        assertThat(cleared).bodyJson().extractingPath("$.manualMonthlyCost").isNull();
        assertThat(cleared).bodyJson().extractingPath("$.targetMonths").isEqualTo(8);
    }

    @Test
    void targetOutOfRangeInvalid() {
        for (String body : List.of("{\"targetMonths\":0}", "{\"targetMonths\":25}", "{}")) {
            var result = patch(ana, body);
            assertThat(result).hasStatus(400);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
        }
    }

    @Test
    void invalidCostRejected() {
        var zero = patch(ana, "{\"manualMonthlyCost\":{\"amount\":\"0\",\"currency\":\"BRL\"}}");
        var foreign = patch(ana, "{\"manualMonthlyCost\":{\"amount\":\"10\",\"currency\":\"USD\"}}");

        assertThat(zero).hasStatus(400);
        assertThat(foreign).hasStatus(422);
        assertThat(foreign).bodyJson().extractingPath("$.code").isEqualTo("budget.currency");
    }

    @Test
    void auditsOnlyChanges() {
        assertThat(patch(ana, "{\"targetMonths\":6}")).hasStatusOk();
        assertThat(audited("reserve.target_changed")).isZero();

        assertThat(patch(ana, "{\"targetMonths\":9}")).hasStatusOk();
        assertThat(audited("reserve.target_changed")).isEqualTo(1);
        assertThat(audited("reserve.cost_changed")).isZero();

        assertThat(patch(ana, "{\"manualMonthlyCost\":null}")).hasStatusOk();
        assertThat(audited("reserve.cost_changed")).isZero();

        String cost = "{\"manualMonthlyCost\":{\"amount\":\"700.00\",\"currency\":\"BRL\"}}";
        assertThat(patch(ana, cost)).hasStatusOk();
        assertThat(patch(ana, cost)).hasStatusOk();
        assertThat(audited("reserve.cost_changed")).isEqualTo(1);
        assertThat(audited("reserve.target_changed")).isEqualTo(1);
    }

    @Test
    void viewerReadsButCannotWrite() {
        UUID vera = users.create("Vera Teste", "vera@example.com");
        workspaces.addMember(ws, vera, Role.VIEWER);

        assertThat(get(vera)).hasStatusOk();
        assertThat(patch(vera, "{\"targetMonths\":12}")).hasStatus(403);
        assertThat(get(ana)).bodyJson().extractingPath("$.targetMonths").isEqualTo(6);
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

        for (MvcTestResult result : List.of(get(ana), patch(ana, "{\"targetMonths\":12}"))) {
            assertThat(result).hasStatus(403);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("module.disabled");
        }
    }

    private void reserveAccount(String initialBalance) {
        jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance, is_reserve)
                        values (:ws, 'Reserva', 'SAVINGS', 'BRL', :balance::numeric, true)""").param("ws", ws).param("balance", initialBalance).update();
    }

    /** Spending of 4000, 4500 and 5000 in the three months before November, taken from the wallet. */
    private void threeMonths() {
        ledger.expense(ws, anaMembership, mercado, "4000.00", LocalDate.of(2026, 8, 10));
        ledger.expense(ws, anaMembership, mercado, "4500.00", LocalDate.of(2026, 9, 10));
        ledger.expense(ws, anaMembership, mercado, "5000.00", LocalDate.of(2026, 10, 10));
    }

    private int audited(String action) {
        return jdbc.sql("select count(*) from audit_events where workspace_id = :ws and action = :action")
                .param("ws", ws)
                .param("action", action)
                .query(Integer.class)
                .single();
    }

    private MvcTestResult get(UUID caller) {
        return send(caller, HttpMethod.GET, "/reserve", null);
    }

    private MvcTestResult patch(UUID caller, String body) {
        return send(caller, HttpMethod.PATCH, "/reserve", body);
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
