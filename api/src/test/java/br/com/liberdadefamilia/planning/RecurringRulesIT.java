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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

/** The family's recurring rules: what they keep, and the planned transactions that follow them. Today is 2026-11-23. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecurringRulesIT.Config.class})
class RecurringRulesIT {

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
    private UUID wallet;
    private UUID mercado;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-23T12:00:00Z"));
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        wallet = ledger.walletOf(ws);
        mercado = ledger.categoryOf(ws, "EXPENSE", "Mercado");
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void createGeneratesUntilEndOfNextMonth() {
        var result = send(ana, HttpMethod.POST, "/recurring-rules", rule("100.00", "MONTHLY", "2026-01-10", null));

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.nextDueOn").isEqualTo("2027-01-10");
        assertThat(result).bodyJson().extractingPath("$.version").isEqualTo(0);
        UUID id = UUID.fromString(JsonPath.read(body(result), "$.id"));
        assertThat(scheduled(id)).containsExactly("2026-11-10 PLANNED 100.0000", "2026-12-10 PLANNED 100.0000");
    }

    @Test
    void descriptionIsStrippedAndBlankBecomesNull() {
        String base = rule("100.00", "MONTHLY", "2026-11-25", null);
        String blank = base.replace("\"frequency\"", "\"description\":\"   \",\"frequency\"");
        String padded = base.replace("\"frequency\"", "\"description\":\"  Luz \",\"frequency\"");

        assertThat(send(ana, HttpMethod.POST, "/recurring-rules", blank))
                .bodyJson()
                .extractingPath("$.description")
                .isNull();
        assertThat(send(ana, HttpMethod.POST, "/recurring-rules", padded))
                .bodyJson()
                .extractingPath("$.description")
                .isEqualTo("Luz");
    }

    @Test
    void endBeforeStartIs422() {
        var result =
                send(ana, HttpMethod.POST, "/recurring-rules", rule("100.00", "MONTHLY", "2026-11-25", "2026-11-24"));

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("recurring-rule.end-before-start");
    }

    @Test
    void templateErrorsUseTransactionCodes() {
        UUID other = users.create("Zeca Teste", "zeca@example.com");
        UUID otherWs = workspaces.createFor(other);
        UUID foreign = ledger.walletOf(otherWs);
        String body = rule("100.00", "MONTHLY", "2026-11-25", null).replace(wallet.toString(), foreign.toString());

        var result = send(ana, HttpMethod.POST, "/recurring-rules", body);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.account-not-found");
        assertThat(count("select count(*) from recurring_rules where workspace_id = :ws", null))
                .isZero();
    }

    @Test
    void invalidIntervalIsRejected() {
        String body = rule("100.00", "MONTHLY", "2026-11-25", null).replace("\"interval\":1", "\"interval\":13");

        var result = send(ana, HttpMethod.POST, "/recurring-rules", body);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
    }

    @Test
    void youthAndViewerCannotWrite() {
        UUID leo = users.create("Leo Teste", "leo@example.com");
        workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        UUID vera = users.create("Vera Teste", "vera@example.com");
        workspaces.addMember(ws, vera, Role.VIEWER, "Vera");
        UUID id = create(rule("100.00", "MONTHLY", "2026-11-25", null));

        for (UUID caller : List.of(leo, vera)) {
            assertThat(send(caller, HttpMethod.POST, "/recurring-rules", rule("5.00", "MONTHLY", "2026-11-25", null)))
                    .hasStatus(403);
            assertThat(send(
                            caller,
                            HttpMethod.PATCH,
                            "/recurring-rules/" + id,
                            update("5.00", 0, "MONTHLY", "2026-11-25")))
                    .hasStatus(403);
            assertThat(send(caller, HttpMethod.POST, "/recurring-rules/" + id + "/archive", null))
                    .hasStatus(403);
        }
        assertThat(count("select count(*) from recurring_rules where workspace_id = :ws", null))
                .isEqualTo(1);
        assertThat(amountOf(id)).isEqualByComparingTo("100.00");
    }

    @Test
    void youthSeesOnlyRulesOnOwnAccounts() {
        UUID leo = users.create("Leo Teste", "leo@example.com");
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        UUID leoWallet = jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, owner_membership_id)
                        values (:ws, 'Carteira de Leo', 'CASH', 'BRL', :owner) returning id""")
                .param("ws", ws)
                .param("owner", leoMembership)
                .query(UUID.class)
                .single();
        UUID adultRule = create(rule("100.00", "MONTHLY", "2026-11-25", null));
        UUID youthRule =
                create(rule("5.00", "MONTHLY", "2026-11-25", null).replace(wallet.toString(), leoWallet.toString()));

        assertThat(ids(send(leo, HttpMethod.GET, "/recurring-rules", null))).containsExactly(youthRule.toString());
        assertThat(ids(send(ana, HttpMethod.GET, "/recurring-rules", null)))
                .containsExactlyInAnyOrder(adultRule.toString(), youthRule.toString());
    }

    @Test
    void editKeepsConfirmedAndSkipped() {
        UUID id = create(rule("100.00", "WEEKLY", "2026-11-25", null));
        UUID november = plannedOn(id, "2026-11-25");
        UUID december2 = plannedOn(id, "2026-12-02");
        assertThat(send(ana, HttpMethod.POST, "/transactions/" + november + "/confirm", """
                        {"version":0,"occurredOn":"2026-11-27"}"""))
                .hasStatusOk();
        assertThat(send(ana, HttpMethod.POST, "/transactions/" + december2 + "/skip", "{\"version\":0}"))
                .hasStatus(204);

        var result = send(ana, HttpMethod.PATCH, "/recurring-rules/" + id, update("120.00", 0, "WEEKLY", "2026-11-25"));

        assertThat(result).hasStatusOk();
        assertThat(count(
                        "select count(*) from transactions where recurring_rule_id = :rule and scheduled_on = date '2026-11-25'",
                        id))
                .isEqualTo(1);
        assertThat(count(
                        "select count(*) from transactions where recurring_rule_id = :rule and scheduled_on = date '2026-12-02'",
                        id))
                .isEqualTo(1);
        assertThat(count("select count(*) from transactions where id = :rule and deleted_at is not null", december2))
                .isEqualTo(1);
        assertThat(scheduled(id))
                .containsExactly(
                        "2026-11-25 CONFIRMED 100.0000",
                        "2026-12-09 PLANNED 120.0000",
                        "2026-12-16 PLANNED 120.0000",
                        "2026-12-23 PLANNED 120.0000",
                        "2026-12-30 PLANNED 120.0000");
    }

    @Test
    void editReplacesOpenFuture() {
        UUID id = create(rule("100.00", "MONTHLY", "2026-11-25", null));

        var result =
                send(ana, HttpMethod.PATCH, "/recurring-rules/" + id, update("120.00", 0, "MONTHLY", "2026-11-25"));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.version").isEqualTo(1);
        assertThat(scheduled(id)).containsExactly("2026-11-25 PLANNED 120.0000", "2026-12-25 PLANNED 120.0000");
    }

    @Test
    void editKeepsOverdue() {
        UUID id = create(rule("100.00", "MONTHLY", "2026-11-22", null));

        var result =
                send(ana, HttpMethod.PATCH, "/recurring-rules/" + id, update("120.00", 0, "MONTHLY", "2026-11-22"));

        assertThat(result).hasStatusOk();
        assertThat(scheduled(id)).containsExactly("2026-11-22 PLANNED 100.0000", "2026-12-22 PLANNED 120.0000");
    }

    /** Moving the day earlier than today within the month must not plan the month a second time. */
    @Test
    void editMovingStartEarlierInTheMonthMakesNoSecondOccurrence() {
        UUID id = create(rule("100.00", "MONTHLY", "2026-11-25", null));
        UUID november = plannedOn(id, "2026-11-25");
        assertThat(send(ana, HttpMethod.POST, "/transactions/" + november + "/confirm", "{\"version\":0}"))
                .hasStatusOk();

        var result =
                send(ana, HttpMethod.PATCH, "/recurring-rules/" + id, update("100.00", 0, "MONTHLY", "2026-11-20"));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.nextDueOn").isEqualTo("2027-01-20");
        assertThat(scheduled(id)).containsExactly("2026-11-25 CONFIRMED 100.0000", "2026-12-20 PLANNED 100.0000");
    }

    @Test
    void createAndEditAreAudited() {
        UUID id = create(rule("100.00", "MONTHLY", "2026-11-25", null));
        assertThat(send(ana, HttpMethod.PATCH, "/recurring-rules/" + id, update("120.00", 0, "MONTHLY", "2026-11-25")))
                .hasStatusOk();

        for (String action : List.of("recurring_rule.created", "recurring_rule.edited")) {
            assertThat(jdbc.sql("""
                                    select count(*) from audit_events
                                    where action = :action and target_type = 'RECURRING_RULE'
                                      and target_id = :rule and workspace_id = :ws""")
                            .param("action", action)
                            .param("rule", id)
                            .param("ws", ws)
                            .query(Integer.class)
                            .single())
                    .isEqualTo(1);
        }
    }

    @Test
    void staleVersionConflicts() {
        UUID id = create(rule("100.00", "MONTHLY", "2026-11-25", null));

        var result =
                send(ana, HttpMethod.PATCH, "/recurring-rules/" + id, update("120.00", 5, "MONTHLY", "2026-11-25"));

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("recurring-rule.version-conflict");
        assertThat(amountOf(id)).isEqualByComparingTo("100.00");
    }

    @Test
    void archiveDropsFutureAndAudits() {
        UUID id = create(rule("100.00", "MONTHLY", "2026-11-22", null));

        assertThat(send(ana, HttpMethod.POST, "/recurring-rules/" + id + "/archive", null))
                .hasStatus(204);
        assertThat(send(ana, HttpMethod.POST, "/recurring-rules/" + id + "/archive", null))
                .hasStatus(204);

        assertThat(scheduled(id)).containsExactly("2026-11-22 PLANNED 100.0000");
        assertThat(count("""
                        select count(*) from audit_events
                        where action = 'recurring_rule.archived' and target_type = 'RECURRING_RULE'
                          and target_id = :rule and workspace_id = :ws""", id)).isEqualTo(1);
        assertThat(ids(send(ana, HttpMethod.GET, "/recurring-rules", null))).isEmpty();
        var edit = send(ana, HttpMethod.PATCH, "/recurring-rules/" + id, update("120.00", 0, "MONTHLY", "2026-11-22"));
        assertThat(edit).hasStatus(404);
        assertThat(edit).bodyJson().extractingPath("$.code").isEqualTo("recurring-rule.not-found");
    }

    @Test
    void archiveOfAnotherFamilysRuleIsNotFound() {
        UUID other = users.create("Zeca Teste", "zeca@example.com");
        UUID otherWs = workspaces.createFor(other);
        UUID foreign = jdbc.sql("""
                        insert into recurring_rules
                            (workspace_id, kind, amount, currency, account_id, category_id, frequency, interval,
                             start_on)
                        values (:ws, 'EXPENSE', 5, 'BRL', :account, :category, 'MONTHLY', 1, '2026-11-25')
                        returning id""")
                .param("ws", otherWs)
                .param("account", ledger.walletOf(otherWs))
                .param("category", ledger.categoryOf(otherWs, "EXPENSE", "Mercado"))
                .query(UUID.class)
                .single();

        var result = send(ana, HttpMethod.POST, "/recurring-rules/" + foreign + "/archive", null);

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("recurring-rule.not-found");
        assertThat(count("select count(*) from recurring_rules where id = :rule and archived_at is null", foreign))
                .isEqualTo(1);
    }

    // --- helpers ---

    private String rule(String amount, String frequency, String startOn, String endOn) {
        return """
                {"kind":"EXPENSE","amount":{"amount":"%s","currency":"BRL"},"accountId":"%s","categoryId":"%s",
                 "frequency":"%s","interval":1,"startOn":"%s","endOn":%s}""".formatted(amount, wallet, mercado, frequency, startOn, endOn == null ? "null" : "\"" + endOn + "\"");
    }

    private String update(String amount, long version, String frequency, String startOn) {
        return """
                {"version":%d,"kind":"EXPENSE","amount":{"amount":"%s","currency":"BRL"},"accountId":"%s",
                 "categoryId":"%s","frequency":"%s","interval":1,"startOn":"%s","endOn":null}""".formatted(version, amount, wallet, mercado, frequency, startOn);
    }

    private UUID create(String body) {
        var result = send(ana, HttpMethod.POST, "/recurring-rules", body);
        assertThat(result).hasStatus(201);
        return UUID.fromString(JsonPath.read(body(result), "$.id"));
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

    /** "date status amount" of the rule's rows that are not deleted, by date. */
    private List<String> scheduled(UUID rule) {
        return jdbc.sql("""
                        select scheduled_on || ' ' || status || ' ' || amount
                        from transactions
                        where recurring_rule_id = :rule and deleted_at is null
                        order by scheduled_on""").param("rule", rule).query(String.class).list();
    }

    private BigDecimal amountOf(UUID rule) {
        return jdbc.sql("select amount from recurring_rules where id = :rule")
                .param("rule", rule)
                .query(BigDecimal.class)
                .single();
    }

    private int count(String sql, UUID id) {
        var statement = jdbc.sql(sql).param("ws", ws);
        if (sql.contains(":rule")) {
            statement = statement.param("rule", id);
        }
        return statement.query(Integer.class).single();
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

    private static List<String> ids(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        return JsonPath.read(body(result), "$[*].id");
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
