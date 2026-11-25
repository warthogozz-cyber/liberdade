package br.com.liberdadefamilia.ventures;

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
import java.time.LocalDate;
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

/** The actual versus planned route: the months, totals and real payback of a project, in the family's time zone. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ActualVsPlannedIT.Config.class})
class ActualVsPlannedIT {

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
    private UUID ws;
    private UUID membership;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-20T12:00:00Z"));
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        membership = workspaces.membershipOf(ws, ana);
        modules(true);
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void endToEndWithTransactions() {
        UUID venture = activeFoodSweets("\"2026-10-01\"");
        mark(venture, "EXPENSE", "800.00", LocalDate.of(2026, 10, 5));
        mark(venture, "INCOME", "300.00", LocalDate.of(2026, 10, 20));
        mark(venture, "INCOME", "900.50", LocalDate.of(2026, 11, 10));

        var result = get(venture);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.currency").isEqualTo("BRL");
        assertThat(result).bodyJson().extractingPath("$.months.length()").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.months[0].month").isEqualTo("2026-11");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.months[0].income.amount")
                .isEqualTo("900.50");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.months[0].income.currency")
                .isEqualTo("BRL");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.months[0].result.amount")
                .isEqualTo("900.50");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.months[0].plannedResult.amount")
                .isEqualTo("540.00");
        assertThat(result).bodyJson().extractingPath("$.months[0].comparison").isEqualTo("ABOVE");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.months[0].difference.amount")
                .isEqualTo("360.50");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.months[1].difference.amount")
                .isEqualTo("1040.00");
        assertThat(result).bodyJson().extractingPath("$.months[1].month").isEqualTo("2026-10");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.months[1].result.amount")
                .isEqualTo("-500.00");
        assertThat(result).bodyJson().extractingPath("$.months[1].comparison").isEqualTo("BELOW");
        assertThat(result).bodyJson().extractingPath("$.totals.income.amount").isEqualTo("1200.50");
        assertThat(result).bodyJson().extractingPath("$.totals.expense.amount").isEqualTo("800.00");
        assertThat(result).bodyJson().extractingPath("$.totals.result.amount").isEqualTo("400.50");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totals.plannedResult.amount")
                .isEqualTo("1080.00");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.totals.difference.amount")
                .isEqualTo("679.50");
        assertThat(result).bodyJson().extractingPath("$.totals.comparison").isEqualTo("BELOW");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.planned.initialInvestment.amount")
                .isEqualTo("800.00");
        assertThat(result).bodyJson().extractingPath("$.planned.paybackState").isEqualTo("MONTHS");
        assertThat(result).bodyJson().extractingPath("$.planned.paybackMonths").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.real.state").isEqualTo("PAID_BACK");
        assertThat(result).bodyJson().extractingPath("$.real.investment.amount").isEqualTo("500.00");
        assertThat(result).bodyJson().extractingPath("$.real.months").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.real.missing").isNull();
    }

    @Test
    void currentMonthUsesFamilyTimezone() {
        jdbc.sql("update workspaces set timezone = 'America/Sao_Paulo' where id = :ws")
                .param("ws", ws)
                .update();
        // 2026-11-30 23:00 in Sao Paulo is already 2026-12-01 in UTC.
        clock.freeze(Instant.parse("2026-12-01T02:00:00Z"));
        UUID venture = activeFoodSweets("\"2026-11-01\"");
        mark(venture, "INCOME", "100.00", LocalDate.of(2026, 11, 15));

        var result = get(venture);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.months.length()").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.months[0].month").isEqualTo("2026-11");
    }

    @Test
    void otherFamilyIs404() {
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID other = workspaces.createFor(bia);
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'ventures', true)")
                .param("ws", other)
                .update();
        UUID foreign = jdbc.sql("""
                        insert into ventures (workspace_id, name, type, currency, created_by)
                        values (:ws, 'Dela', 'OTHER', 'BRL', :m)
                        returning id""")
                .param("ws", other)
                .param("m", workspaces.membershipOf(other, bia))
                .query(UUID.class)
                .single();

        var result = get(foreign);

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("venture.not-found");
    }

    @Test
    void moduleOffIs403() {
        UUID venture = activeFoodSweets("\"2026-10-01\"");
        modules(false);

        var result = get(venture);

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("module.disabled");
    }

    private UUID activeFoodSweets(String day) {
        var created = send(HttpMethod.POST, "/ventures", "{\"templateKey\":\"food-sweets\",\"name\":\"Doces da Ana\"}");
        assertThat(created).hasStatus(201);
        UUID id = UUID.fromString(read(created, "$.id"));
        var status = send(
                HttpMethod.POST,
                "/ventures/" + id + "/status",
                "{\"version\":0,\"status\":\"ACTIVE\",\"on\":" + day + "}");
        assertThat(status).hasStatus(200);
        return id;
    }

    private void mark(UUID venture, String kind, String amount, LocalDate on) {
        UUID category = ledger.categoryOf(ws, kind, "INCOME".equals(kind) ? "Salário" : "Mercado");
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on, account_id, category_id,
                             member_id, created_by, status, venture_id)
                        values (:id, :ws, :kind, cast(:amount as numeric), 'BRL', :on, :account, :category,
                                :member, :member, 'CONFIRMED', :venture)""")
                .param("id", TestIds.v7())
                .param("ws", ws)
                .param("kind", kind)
                .param("amount", amount)
                .param("on", on)
                .param("account", ledger.walletOf(ws))
                .param("category", category)
                .param("member", membership)
                .param("venture", venture)
                .update();
    }

    private void modules(boolean on) {
        jdbc.sql("delete from workspace_modules where workspace_id = :ws and module_key = 'ventures'")
                .param("ws", ws)
                .update();
        if (on) {
            jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'ventures', true)")
                    .param("ws", ws)
                    .update();
        }
    }

    private MvcTestResult get(UUID venture) {
        return send(HttpMethod.GET, "/ventures/" + venture + "/actual-vs-planned", null);
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

    private static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
