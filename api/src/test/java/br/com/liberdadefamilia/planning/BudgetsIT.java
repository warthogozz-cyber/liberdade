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

/** The family's monthly limits per category and what was spent against them. Today is 2026-11-20. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, BudgetsIT.Config.class})
class BudgetsIT {

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
    void createReadUpdate() {
        var created = put(ana, "2026-11", mercado, "1200.00", null);
        assertThat(created).hasStatus(201);
        assertThat(created).bodyJson().extractingPath("$.version").isEqualTo(0);

        var month = send(ana, HttpMethod.GET, "/budgets?month=2026-11", null);
        assertThat(month).hasStatusOk();
        assertThat(month).bodyJson().extractingPath("$.month").isEqualTo("2026-11");
        assertThat(month).bodyJson().extractingPath("$.lines.length()").isEqualTo(1);
        assertThat(month).bodyJson().extractingPath("$.lines[0].categoryId").isEqualTo(mercado.toString());
        assertThat(month).bodyJson().extractingPath("$.lines[0].limit.amount").isEqualTo("1200.00");
        assertThat(month).bodyJson().extractingPath("$.lines[0].limit.currency").isEqualTo("BRL");

        var updated = put(ana, "2026-11", mercado, "1500.00", 0L);
        assertThat(updated).hasStatusOk();
        assertThat(updated).bodyJson().extractingPath("$.limit.amount").isEqualTo("1500.00");
        assertThat(updated).bodyJson().extractingPath("$.version").isEqualTo(1);
    }

    @Test
    void staleVersionConflicts() {
        assertThat(put(ana, "2026-11", mercado, "1200.00", null)).hasStatus(201);
        assertThat(put(ana, "2026-11", mercado, "1500.00", 0L)).hasStatusOk();

        var stale = put(ana, "2026-11", mercado, "900.00", 0L);
        assertThat(stale).hasStatus(409);
        assertThat(stale).bodyJson().extractingPath("$.code").isEqualTo("budget.version-conflict");

        var again = put(ana, "2026-11", mercado, "900.00", null);
        assertThat(again).hasStatus(409);
        assertThat(again).bodyJson().extractingPath("$.code").isEqualTo("budget.version-conflict");

        var absent = put(ana, "2026-12", mercado, "900.00", 0L);
        assertThat(absent).hasStatus(409);
        assertThat(limitOf("2026-11", mercado)).isEqualByComparingTo("1500");
    }

    @Test
    void spentAndStateFromPrdExample() {
        put(ana, "2026-11", mercado, "1200.00", null);
        ledger.expense(ws, anaMembership, mercado, "960.00", LocalDate.of(2026, 11, 5));

        var near = send(ana, HttpMethod.GET, "/budgets?month=2026-11", null);
        assertThat(near).bodyJson().extractingPath("$.lines[0].spent.amount").isEqualTo("960.00");
        assertThat(near).bodyJson().extractingPath("$.lines[0].usagePercent").isEqualTo(80);
        assertThat(near).bodyJson().extractingPath("$.lines[0].state").isEqualTo("NEAR");
        assertThat(near).bodyJson().extractingPath("$.lines[0].overBy").isNull();

        ledger.expense(ws, anaMembership, mercado, "300.00", LocalDate.of(2026, 11, 6));

        var over = send(ana, HttpMethod.GET, "/budgets?month=2026-11", null);
        assertThat(over).bodyJson().extractingPath("$.lines[0].usagePercent").isEqualTo(105);
        assertThat(over).bodyJson().extractingPath("$.lines[0].state").isEqualTo("OVER");
        assertThat(over).bodyJson().extractingPath("$.lines[0].overBy.amount").isEqualTo("60.00");
    }

    @Test
    void parentLimitIncludesChildren() {
        UUID feira = category("Feira", mercado);
        put(ana, "2026-11", mercado, "500.00", null);
        ledger.expense(ws, anaMembership, feira, "100.00", LocalDate.of(2026, 11, 5));

        var month = send(ana, HttpMethod.GET, "/budgets?month=2026-11", null);

        assertThat(month).bodyJson().extractingPath("$.lines.length()").isEqualTo(1);
        assertThat(month).bodyJson().extractingPath("$.lines[0].spent.amount").isEqualTo("100.00");
    }

    @Test
    void childLimitCountsOnlyItself() {
        UUID feira = category("Feira", mercado);
        put(ana, "2026-11", mercado, "500.00", null);
        put(ana, "2026-11", feira, "500.00", null);
        ledger.expense(ws, anaMembership, mercado, "40.00", LocalDate.of(2026, 11, 5));
        ledger.expense(ws, anaMembership, feira, "60.00", LocalDate.of(2026, 11, 6));

        var month = send(ana, HttpMethod.GET, "/budgets?month=2026-11", null);

        assertThat(month).bodyJson().extractingPath("$.lines.length()").isEqualTo(2);
        // The lines come in the order of the category names: Feira, then Mercado.
        assertThat(month).bodyJson().extractingPath("$.lines[0].categoryId").isEqualTo(feira.toString());
        assertThat(month).bodyJson().extractingPath("$.lines[0].spent.amount").isEqualTo("60.00");
        assertThat(month).bodyJson().extractingPath("$.lines[1].categoryId").isEqualTo(mercado.toString());
        assertThat(month).bodyJson().extractingPath("$.lines[1].spent.amount").isEqualTo("100.00");
    }

    @Test
    void plannedAndOtherMonthsDoNotCount() {
        put(ana, "2026-11", mercado, "500.00", null);
        ledger.insertPlanned(
                ws,
                ledger.walletOf(ws),
                mercado,
                Money.of(new BigDecimal("80.00"), BRL),
                LocalDate.of(2026, 11, 25),
                null,
                anaMembership);
        ledger.expense(ws, anaMembership, mercado, "70.00", LocalDate.of(2026, 12, 2));
        ledger.expense(ws, anaMembership, mercado, "60.00", LocalDate.of(2026, 10, 30));

        var month = send(ana, HttpMethod.GET, "/budgets?month=2026-11", null);

        assertThat(month).bodyJson().extractingPath("$.lines[0].spent.amount").isEqualTo("0.00");
        assertThat(month).bodyJson().extractingPath("$.lines[0].usagePercent").isEqualTo(0);
        assertThat(month).bodyJson().extractingPath("$.lines[0].state").isEqualTo("UNDER");
    }

    @Test
    void incomeCategoryRejected() {
        UUID salario = ledger.categoryOf(ws, "INCOME", "Salário");

        var result = put(ana, "2026-11", salario, "100.00", null);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("budget.category-not-expense");
    }

    @Test
    void otherFamilyCategoryNotFound() {
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID otherWs = workspaces.createFor(bia);
        UUID foreign = ledger.categoryOf(otherWs, "EXPENSE", "Mercado");

        var result = put(ana, "2026-11", foreign, "100.00", null);

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("category.not-found");
    }

    @Test
    void zeroLimitInvalid() {
        var zero = put(ana, "2026-11", mercado, "0.00", null);
        assertThat(zero).hasStatus(400);
        assertThat(zero).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");

        var negative = put(ana, "2026-11", mercado, "-5.00", null);
        assertThat(negative).hasStatus(400);
        assertThat(negative).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
    }

    @Test
    void wrongMonthFormatInvalid() {
        for (String month : List.of("2026-13", "2026-1", "202611", "nov")) {
            var result = put(ana, month, mercado, "100.00", null);
            assertThat(result).as(month).hasStatus(400);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
        }
        var list = send(ana, HttpMethod.GET, "/budgets?month=2026-13", null);
        assertThat(list).hasStatus(400);
        assertThat(list).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
        assertThat(send(ana, HttpMethod.GET, "/budgets", null)).hasStatus(400);
    }

    @Test
    void otherCurrencyRejected() {
        String body = "{\"limitAmount\":{\"amount\":\"100.00\",\"currency\":\"USD\"},\"version\":null}";

        var result = send(ana, HttpMethod.PUT, "/budgets/2026-11/" + mercado, body);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("budget.currency");
    }

    @Test
    void deleteIsIdempotent() {
        put(ana, "2026-11", mercado, "100.00", null);

        assertThat(send(ana, HttpMethod.DELETE, "/budgets/2026-11/" + mercado, null))
                .hasStatus(204);
        assertThat(send(ana, HttpMethod.DELETE, "/budgets/2026-11/" + mercado, null))
                .hasStatus(204);

        var month = send(ana, HttpMethod.GET, "/budgets?month=2026-11", null);
        assertThat(month).bodyJson().extractingPath("$.lines.length()").isEqualTo(0);
    }

    @Test
    void copyPreviousSkipsExistingAndArchived() {
        UUID cursos = category("Cursos", null);
        UUID velha = category("Velha", null);
        put(ana, "2026-10", mercado, "100.00", null);
        put(ana, "2026-10", cursos, "200.00", null);
        put(ana, "2026-10", velha, "300.00", null);
        jdbc.sql("update categories set archived_at = now() where id = :id")
                .param("id", velha)
                .update();
        put(ana, "2026-11", mercado, "999.00", null);

        var result = send(ana, HttpMethod.POST, "/budgets/2026-11/copy-previous", null);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.copied").isEqualTo(1);
        var month = send(ana, HttpMethod.GET, "/budgets?month=2026-11", null);
        assertThat(month).bodyJson().extractingPath("$.lines.length()").isEqualTo(2);
        assertThat(limitOf("2026-11", mercado)).isEqualByComparingTo("999");
        assertThat(limitOf("2026-11", cursos)).isEqualByComparingTo("200");
    }

    @Test
    void viewerReadsButCannotWrite() {
        UUID vera = users.create("Vera Teste", "vera@example.com");
        workspaces.addMember(ws, vera, Role.VIEWER);
        put(ana, "2026-11", mercado, "100.00", null);

        assertThat(send(vera, HttpMethod.GET, "/budgets?month=2026-11", null)).hasStatusOk();
        var write = put(vera, "2026-11", mercado, "300.00", 0L);
        assertThat(write).hasStatus(403);
        assertThat(limitOf("2026-11", mercado)).isEqualByComparingTo("100");
    }

    @Test
    void youthIsForbidden() {
        UUID leo = users.create("Leo Teste", "leo@example.com");
        workspaces.addMember(ws, leo, Role.YOUTH);

        var result = send(leo, HttpMethod.GET, "/budgets?month=2026-11", null);

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
    }

    @Test
    void moduleOff() {
        assertThat(send(ana, HttpMethod.PUT, "/modules/planning", "{\"enabled\":false}"))
                .hasStatusOk();

        var read = send(ana, HttpMethod.GET, "/budgets?month=2026-11", null);
        var write = put(ana, "2026-11", mercado, "100.00", null);

        for (MvcTestResult result : List.of(read, write)) {
            assertThat(result).hasStatus(403);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("module.disabled");
        }
    }

    private UUID category(String name, UUID parentId) {
        return jdbc.sql("""
                        insert into categories (workspace_id, parent_id, kind, name)
                        values (:ws, :parent, 'EXPENSE', :name)
                        returning id""")
                .param("ws", ws)
                .param("parent", parentId)
                .param("name", name)
                .query(UUID.class)
                .single();
    }

    private BigDecimal limitOf(String month, UUID category) {
        return jdbc.sql("""
                        select limit_amount from budgets
                        where workspace_id = :ws and category_id = :category and month = cast(:month as date)""")
                .param("ws", ws)
                .param("category", category)
                .param("month", month + "-01")
                .query(BigDecimal.class)
                .single();
    }

    private MvcTestResult put(UUID caller, String month, UUID category, String amount, Long version) {
        String body =
                "{\"limitAmount\":{\"amount\":\"%s\",\"currency\":\"BRL\"},\"version\":%s}".formatted(amount, version);
        return send(caller, HttpMethod.PUT, "/budgets/" + month + "/" + category, body);
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
