package br.com.liberdadefamilia.planning;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.shared.Money;
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

/** The month's spending split into fixed and variable (D14). Today is 2026-11-20. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SpendingSplitIT.Config.class})
class SpendingSplitIT {

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

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-20T12:00:00Z"));
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        anaMembership = workspaces.membershipOf(ws, ana);
        wallet = ledger.walletOf(ws);
        mercado = ledger.categoryOf(ws, "EXPENSE", "Mercado");
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void ruleAndFixedCategoryAreFixed() {
        UUID moradia = ledger.categoryOf(ws, "EXPENSE", "Moradia");
        UUID lazer = ledger.categoryOf(ws, "EXPENSE", "Lazer");
        jdbc.sql("update categories set is_fixed = true where id in (:ids)")
                .param("ids", List.of(moradia, lazer))
                .update();
        UUID clube = child(lazer, "Clube");
        confirmedFromRule("2000.00", LocalDate.of(2026, 11, 10));
        ledger.expense(ws, anaMembership, moradia, "300.00", LocalDate.of(2026, 11, 12));
        ledger.expense(ws, anaMembership, clube, "50.00", LocalDate.of(2026, 11, 13));
        ledger.expense(ws, anaMembership, mercado, "400.00", LocalDate.of(2026, 11, 14));
        // Outside the month: never counts.
        ledger.expense(ws, anaMembership, mercado, "999.00", LocalDate.of(2026, 10, 31));
        ledger.expense(ws, anaMembership, moradia, "999.00", LocalDate.of(2026, 12, 1));

        var result = get(ana, "2026-11");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.month").isEqualTo("2026-11");
        assertThat(result).bodyJson().extractingPath("$.fixed.amount").isEqualTo("2350.00");
        assertThat(result).bodyJson().extractingPath("$.fixed.currency").isEqualTo("BRL");
        assertThat(result).bodyJson().extractingPath("$.variable.amount").isEqualTo("400.00");
    }

    @Test
    void plannedDoesNotCount() {
        ledger.insertPlanned(ws, wallet, mercado, Money.brl("700.00"), LocalDate.of(2026, 11, 25), null, anaMembership);
        ledger.expense(ws, anaMembership, mercado, "40.00", LocalDate.of(2026, 11, 3));

        var result = get(ana, "2026-11");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.fixed.amount").isEqualTo("0.00");
        assertThat(result).bodyJson().extractingPath("$.variable.amount").isEqualTo("40.00");
    }

    @Test
    void monthIsRequiredAndChecked() {
        for (String query : new String[] {"", "?month=2026-13", "?month=2026-1", "?month=novembro"}) {
            var result = send(ana, HttpMethod.GET, "/spending-split" + query, null);
            assertThat(result).hasStatus(400);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
        }
    }

    @Test
    void viewerReads() {
        UUID vera = users.create("Vera Teste", "vera@example.com");
        workspaces.addMember(ws, vera, Role.VIEWER);

        assertThat(get(vera, "2026-11")).hasStatusOk();
    }

    @Test
    void youthIsForbidden() {
        UUID leo = users.create("Leo Teste", "leo@example.com");
        workspaces.addMember(ws, leo, Role.YOUTH);

        var result = get(leo, "2026-11");

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
    }

    @Test
    void moduleOff() {
        assertThat(send(ana, HttpMethod.PUT, "/modules/planning", "{\"enabled\":false}"))
                .hasStatusOk();

        var result = get(ana, "2026-11");

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("module.disabled");
    }

    /** A confirmed expense that came from a recurring rule, in a category that is not fixed. */
    private void confirmedFromRule(String amount, LocalDate on) {
        UUID rule = jdbc.sql("""
                        insert into recurring_rules
                            (workspace_id, kind, amount, currency, account_id, category_id, frequency, "interval",
                             start_on, created_by)
                        values (:ws, 'EXPENSE', :amount::numeric, 'BRL', :wallet, :mercado, 'MONTHLY', 1, :on,
                                :membership)
                        returning id""")
                .param("ws", ws)
                .param("amount", amount)
                .param("wallet", wallet)
                .param("mercado", mercado)
                .param("on", on)
                .param("membership", anaMembership)
                .query(UUID.class)
                .single();
        UUID planned = ledger.insertPlanned(ws, wallet, mercado, Money.brl(amount), on, rule, anaMembership);
        jdbc.sql("update transactions set status = 'CONFIRMED' where id = :id")
                .param("id", planned)
                .update();
    }

    private UUID child(UUID parent, String name) {
        return jdbc.sql("""
                        insert into categories (workspace_id, parent_id, kind, name)
                        values (:ws, :parent, 'EXPENSE', :name)
                        returning id""")
                .param("ws", ws)
                .param("parent", parent)
                .param("name", name)
                .query(UUID.class)
                .single();
    }

    private MvcTestResult get(UUID caller, String month) {
        return send(caller, HttpMethod.GET, "/spending-split?month=" + month, null);
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
