package br.com.liberdadefamilia.planning;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.planning.internal.RecurrenceJob;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
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

/** The planning module switch: off, the family is left alone; on, the horizon is filled at once. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ModuleOffIT.Config.class})
class ModuleOffIT {

    private static final Currency BRL = Currency.getInstance("BRL");

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
    RecurrenceJob job;

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
    private UUID ws;
    private UUID rule;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-23T12:00:00Z"));
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        UUID membership = workspaces.membershipOf(ws, ana);
        rule = jdbc.sql("""
                        insert into recurring_rules
                            (workspace_id, kind, amount, currency, account_id, category_id, frequency, interval,
                             start_on, next_due_on, created_by)
                        values (:ws, 'EXPENSE', 100, 'BRL', :account, :category, 'MONTHLY', 1,
                                date '2026-11-10', date '2026-11-10', :createdBy)
                        returning id""")
                .param("ws", ws)
                .param("account", ledger.walletOf(ws))
                .param("category", ledger.categoryOf(ws, "EXPENSE", "Mercado"))
                .param("createdBy", membership)
                .query(UUID.class)
                .single();
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void jobSkipsFamilyWithModuleOff() {
        job.run();
        assertThat(setPlanning(false)).hasStatusOk();
        assertThat(dates()).containsExactly("2026-11-10", "2026-12-10");

        clock.freeze(Instant.parse("2026-12-23T12:00:00Z"));
        job.run();

        assertThat(dates()).containsExactly("2026-11-10", "2026-12-10");
    }

    @Test
    void turningOnFillsTheHorizon() {
        assertThat(setPlanning(false)).hasStatusOk();
        assertThat(dates()).isEmpty();

        assertThat(setPlanning(true)).hasStatusOk();

        assertThat(dates()).containsExactly("2026-11-10", "2026-12-10");
    }

    @Test
    void routesAre403() {
        UUID planned = ledger.insertPlanned(
                ws,
                ledger.walletOf(ws),
                ledger.categoryOf(ws, "EXPENSE", "Mercado"),
                Money.of(new BigDecimal("5.00"), BRL),
                LocalDate.of(2026, 11, 25),
                null,
                workspaces.membershipOf(ws, ana));
        assertThat(setPlanning(false)).hasStatusOk();

        List<MvcTestResult> results = List.of(
                send(HttpMethod.GET, "/recurring-rules", null),
                send(HttpMethod.POST, "/recurring-rules", "{}"),
                send(HttpMethod.PATCH, "/recurring-rules/" + rule, "{}"),
                send(HttpMethod.POST, "/recurring-rules/" + rule + "/archive", null),
                send(HttpMethod.POST, "/transactions/" + planned + "/confirm", "{\"version\":0}"),
                send(HttpMethod.POST, "/transactions/" + planned + "/skip", "{\"version\":0}"));

        for (MvcTestResult result : results) {
            assertThat(result).hasStatus(403);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("module.disabled");
        }
    }

    @Test
    void nothingIsDeleted() {
        job.run();
        List<String> before = dates();
        assertThat(before).hasSize(2);

        assertThat(setPlanning(false)).hasStatusOk();
        assertThat(setPlanning(true)).hasStatusOk();

        assertThat(dates()).isEqualTo(before);
        assertThat(jdbc.sql("select count(*) from recurring_rules where id = :rule and archived_at is null")
                        .param("rule", rule)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    private MvcTestResult setPlanning(boolean enabled) {
        return send(HttpMethod.PUT, "/modules/planning", "{\"enabled\":" + enabled + "}");
    }

    private List<String> dates() {
        return jdbc.sql("""
                        select cast(scheduled_on as text) from transactions
                        where recurring_rule_id = :rule and deleted_at is null
                        order by scheduled_on""").param("rule", rule).query(String.class).list();
    }

    private MvcTestResult send(HttpMethod method, String path, String body) {
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
