package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.FamilyActivity;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** A planned transaction counts in no total, and shows in the month's list only while the planning module is on. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PlannedReadsIT.Config.class})
class PlannedReadsIT {

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
    FamilyActivity activity;

    private UUID ana;
    private UUID ws;
    private UUID membership;
    private UUID wallet;
    private UUID mercado;
    private UUID lazer;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-23T12:00:00Z"));
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        membership = workspaces.membershipOf(ws, ana);
        wallet = ledger.walletOf(ws);
        mercado = ledger.categoryOf(ws, "EXPENSE", "Mercado");
        lazer = ledger.categoryOf(ws, "EXPENSE", "Lazer");
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void plannedDoesNotCountAnywhere() {
        ledger.expense(ws, membership, mercado, "50.00", LocalDate.of(2026, 11, 10));
        String accounts = body(get(ana, "/accounts"));
        String dashboard = body(get(ana, "/dashboard?month=2026-11"));
        String categories = body(get(ana, "/categories?kind=EXPENSE"));

        for (int day = 20; day <= 22; day++) {
            planned(wallet, lazer, "900.00", LocalDate.of(2026, 11, day), null, membership);
        }

        assertThat(body(get(ana, "/accounts"))).isEqualTo(accounts);
        assertThat(body(get(ana, "/dashboard?month=2026-11"))).isEqualTo(dashboard);
        assertThat(body(get(ana, "/categories?kind=EXPENSE"))).isEqualTo(categories);
        assertThat(JsonPath.<String>read(dashboard, "$.expenses.amount")).isEqualTo("50.00");
        assertThat(JsonPath.<List<Object>>read(dashboard, "$.byCategory")).hasSize(1);
    }

    @Test
    void plannedTransferDoesNotMoveBalances() {
        UUID savings = jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency)
                        values (:ws, 'Poupanca', 'SAVINGS', 'BRL') returning id""").param("ws", ws).query(UUID.class).single();
        String accounts = body(get(ana, "/accounts"));

        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on, account_id, to_account_id,
                             member_id, created_by, status, scheduled_on)
                        values (uuidv7(), :ws, 'TRANSFER', 300.00, 'BRL', date '2026-11-25', :from, :to,
                                :m, :m, 'PLANNED', date '2026-11-25')""")
                .param("ws", ws)
                .param("from", wallet)
                .param("to", savings)
                .param("m", membership)
                .update();

        assertThat(body(get(ana, "/accounts"))).isEqualTo(accounts);
    }

    @Test
    void plannedAloneIsNotFamilyActivity() {
        planned(wallet, lazer, "10.00", LocalDate.of(2026, 11, 20), null, membership);

        assertThat(activity.withTransactions(Set.of(ws))).isEmpty();

        ledger.expense(ws, membership, mercado, "50.00", LocalDate.of(2026, 11, 10));
        assertThat(activity.withTransactions(Set.of(ws))).containsExactly(ws);
    }

    @Test
    void listShowsPlannedWithModuleOn() {
        UUID rule = rule();
        UUID planned = planned(wallet, lazer, "90.00", LocalDate.of(2026, 11, 28), rule, membership);

        var result = get(ana, "/transactions?month=2026-11");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[0].id").isEqualTo(planned.toString());
        assertThat(result).bodyJson().extractingPath("$[0].status").isEqualTo("PLANNED");
        assertThat(result).bodyJson().extractingPath("$[0].scheduledOn").isEqualTo("2026-11-28");
        assertThat(result).bodyJson().extractingPath("$[0].recurringRuleId").isEqualTo(rule.toString());
    }

    @Test
    void confirmedTransactionHasStatusConfirmed() {
        ledger.expense(ws, membership, mercado, "50.00", LocalDate.of(2026, 11, 10));

        var result = get(ana, "/transactions?month=2026-11");

        assertThat(result).bodyJson().extractingPath("$[0].status").isEqualTo("CONFIRMED");
        assertThat(result).bodyJson().extractingPath("$[0].scheduledOn").isNull();
        assertThat(result).bodyJson().extractingPath("$[0].recurringRuleId").isNull();
    }

    @Test
    void listHidesPlannedWithModuleOff() {
        UUID planned = planned(wallet, lazer, "90.00", LocalDate.of(2026, 11, 28), null, membership);

        assertThat(setPlanning(false)).hasStatusOk();
        assertThat(ids(get(ana, "/transactions?month=2026-11"))).isEmpty();

        assertThat(setPlanning(true)).hasStatusOk();
        assertThat(ids(get(ana, "/transactions?month=2026-11"))).containsExactly(planned.toString());
    }

    @Test
    void overdueShowsInCurrentMonthOnly() {
        clock.freeze(Instant.parse("2026-12-05T12:00:00Z"));
        UUID overdue = planned(wallet, lazer, "90.00", LocalDate.of(2026, 11, 28), null, membership);

        assertThat(ids(get(ana, "/transactions?month=2026-12"))).containsExactly(overdue.toString());
        assertThat(ids(get(ana, "/transactions?month=2026-11"))).containsExactly(overdue.toString());
        assertThat(ids(get(ana, "/transactions?month=2027-01"))).isEmpty();
    }

    @Test
    void patchPlannedIs422() {
        UUID planned = planned(wallet, lazer, "90.00", LocalDate.of(2026, 11, 28), null, membership);

        var result = mvc.patch()
                .uri("/v1/workspaces/" + ws + "/transactions/" + planned)
                .with(TestUsers.signedIn(ana, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version":0,"kind":"EXPENSE","amount":{"amount":"91.00","currency":"BRL"},
                         "occurredOn":"2026-11-28","accountId":"%s","categoryId":"%s"}""".formatted(wallet, lazer))
                .exchange();

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.planned");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Confirme o previsto antes de editar.");
    }

    @Test
    void youthDoesNotSeeAdultPlanned() {
        UUID leo = users.create("Leo Teste", "leo@example.com");
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        UUID leoWallet = jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, owner_membership_id)
                        values (:ws, 'Carteira de Leo', 'WALLET', 'BRL', :m) returning id""")
                .param("ws", ws)
                .param("m", leoMembership)
                .query(UUID.class)
                .single();
        UUID adults = planned(wallet, lazer, "90.00", LocalDate.of(2026, 11, 28), null, membership);
        UUID his = planned(leoWallet, lazer, "5.00", LocalDate.of(2026, 11, 27), null, leoMembership);

        assertThat(ids(get(leo, "/transactions?month=2026-11"))).containsExactly(his.toString());
        assertThat(ids(get(ana, "/transactions?month=2026-11"))).containsExactly(adults.toString(), his.toString());
    }

    private UUID planned(UUID account, UUID category, String amount, LocalDate on, UUID rule, UUID by) {
        return ledger.insertPlanned(ws, account, category, Money.brl(amount), on, rule, by);
    }

    private UUID rule() {
        return jdbc.sql("""
                        insert into recurring_rules
                            (workspace_id, kind, amount, currency, account_id, category_id, frequency, interval,
                             start_on)
                        values (:ws, 'EXPENSE', 90, 'BRL', :account, :category, 'MONTHLY', 1, '2026-11-28')
                        returning id""")
                .param("ws", ws)
                .param("account", wallet)
                .param("category", lazer)
                .query(UUID.class)
                .single();
    }

    private MvcTestResult setPlanning(boolean enabled) {
        return mvc.put()
                .uri("/v1/workspaces/" + ws + "/modules/planning")
                .with(TestUsers.signedIn(ana, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":" + enabled + "}")
                .exchange();
    }

    private MvcTestResult get(UUID caller, String path) {
        return mvc.get()
                .uri("/v1/workspaces/" + ws + path)
                .with(TestUsers.signedIn(caller, null))
                .exchange();
    }

    private static List<String> ids(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        return JsonPath.read(body(result), "$[*].id");
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
