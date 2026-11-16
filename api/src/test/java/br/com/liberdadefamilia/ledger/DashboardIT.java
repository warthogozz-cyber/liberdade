package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The month's expenses and the family's balance, and each account's balance, all computed from the transactions. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DashboardIT {

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

    private UUID userId;
    private UUID workspaceId;
    private UUID membershipId;
    private UUID mercadoId;

    private static final String MONTH = "2026-11";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 5);

    @BeforeEach
    void family() {
        userId = users.create("Ana Teste", "ana@example.com");
        workspaceId = workspaces.createFor(userId);
        membershipId = workspaces.membershipOf(workspaceId, userId);
        mercadoId = ledger.categoryOf(workspaceId, "EXPENSE", "Mercado");
    }

    /** The month is the family's calendar month: the 30th belongs to November, the 1st to December. */
    @Test
    void monthBoundary() {
        ledger.expense(workspaceId, membershipId, mercadoId, "10.00", LocalDate.of(2026, 11, 30));
        ledger.expense(workspaceId, membershipId, mercadoId, "20.00", LocalDate.of(2026, 12, 1));

        var november = dashboard(userId, "2026-11");
        var december = dashboard(userId, "2026-12");

        assertThat(november).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        assertThat(november).bodyJson().isLenientlyEqualTo("""
                {"month":"2026-11",
                 "expenses":{"amount":"10.00","currency":"BRL"},
                 "balance":{"amount":"-10.00","currency":"BRL"}}""");
        assertThat(december).bodyJson().isLenientlyEqualTo("""
                {"month":"2026-12",
                 "expenses":{"amount":"20.00","currency":"BRL"},
                 "balance":{"amount":"-30.00","currency":"BRL"}}""");
    }

    @Test
    void incomeNotInExpenses() {
        postIncome("100.00", "2026-11-05");
        ledger.expense(workspaceId, membershipId, mercadoId, "30.00", LocalDate.of(2026, 11, 6));

        var result = dashboard(userId, "2026-11");

        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"month":"2026-11",
                 "income":{"amount":"100.00","currency":"BRL"},
                 "expenses":{"amount":"30.00","currency":"BRL"},
                 "result":{"amount":"70.00","currency":"BRL"},
                 "balance":{"amount":"70.00","currency":"BRL"}}""");
    }

    @Test
    void emptyMonth() {
        var result = dashboard(userId, "2026-11");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"month":"2026-11",
                 "income":{"amount":"0.00","currency":"BRL"},
                 "expenses":{"amount":"0.00","currency":"BRL"},
                 "result":{"amount":"0.00","currency":"BRL"},
                 "balance":{"amount":"0.00","currency":"BRL"},
                 "byCategory":[]}""");
    }

    @Test
    void deletedLeftOut() {
        ledger.expense(workspaceId, membershipId, mercadoId, "10.00", LocalDate.of(2026, 11, 6));
        jdbc.sql("update transactions set deleted_at = now() where workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .update();

        var dashboard = dashboard(userId, "2026-11");
        var accounts = accounts(userId);

        assertThat(dashboard).bodyJson().extractingPath("$.expenses.amount").isEqualTo("0.00");
        assertThat(dashboard).bodyJson().extractingPath("$.balance.amount").isEqualTo("0.00");
        assertThat(accounts).bodyJson().extractingPath("$[0].balance.amount").isEqualTo("0.00");
    }

    @Test
    void accountBalanceComputed() {
        jdbc.sql("update accounts set initial_balance = 50.00 where id = :id")
                .param("id", ledger.walletOf(workspaceId))
                .update();
        ledger.expense(workspaceId, membershipId, mercadoId, "12.34", LocalDate.of(2026, 11, 2));

        var result = accounts(userId);

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                [{"id":"%s","name":"Carteira","type":"CASH","currency":"BRL",
                  "balance":{"amount":"37.66","currency":"BRL"}}]""".formatted(ledger.walletOf(workspaceId)));
    }

    /**
     * Accounts come by name, without the archived ones. The family's balance adds only the accounts in its base
     * currency.
     */
    @Test
    void accountsActiveByNameThenArchived() {
        account("Poupança", "SAVINGS", "BRL", "100.00", true);
        account("Banco", "CHECKING", "BRL", "25.00", false);
        account("Viagem", "CASH", "USD", "40.00", false);

        var accounts = accounts(userId);
        var dashboard = dashboard(userId, "2026-11");

        assertThat(accounts).bodyJson().isLenientlyEqualTo("""
                [{"name":"Banco","type":"CHECKING","balance":{"amount":"25.00","currency":"BRL"}},
                 {"name":"Carteira","type":"CASH","balance":{"amount":"0.00","currency":"BRL"}},
                 {"name":"Viagem","type":"CASH","currency":"USD","balance":{"amount":"40.00","currency":"USD"}},
                 {"name":"Poupança","type":"SAVINGS","balance":{"amount":"100.00","currency":"BRL"}}]""");
        assertThat(dashboard)
                .bodyJson()
                .extractingPath("$.balance")
                .isEqualTo(Map.of("amount", "25.00", "currency", "BRL"));
    }

    /** An expense in another currency is not added to the month's expenses: amounts in two currencies don't add up. */
    @Test
    void expensesInBaseCurrencyOnly() {
        UUID travel = account("Viagem", "CASH", "USD", "0.00", false);
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on,
                             account_id, category_id, member_id, created_by)
                        values (:id, :workspaceId, 'EXPENSE', 7.00, 'USD', '2026-11-03',
                                :account, :category, :membershipId, :membershipId)""")
                .param("id", TestIds.v7())
                .param("workspaceId", workspaceId)
                .param("account", travel)
                .param("category", mercadoId)
                .param("membershipId", membershipId)
                .update();
        ledger.expense(workspaceId, membershipId, mercadoId, "10.00", LocalDate.of(2026, 11, 4));

        var dashboard = dashboard(userId, "2026-11");

        assertThat(dashboard).bodyJson().isLenientlyEqualTo("""
                {"month":"2026-11",
                 "expenses":{"amount":"10.00","currency":"BRL"},
                 "balance":{"amount":"-10.00","currency":"BRL"}}""");
        assertThat(dashboard).bodyJson().extractingPath("$.byCategory").asList().hasSize(1);
    }

    /** A transfer leaves its account and enters the other one; it is not an expense. */
    @Test
    void transferMovesBetweenAccounts() {
        UUID bank = account("Banco", "CHECKING", "BRL", "0.00", false);
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on,
                             account_id, to_account_id, member_id, created_by)
                        values (:id, :workspaceId, 'TRANSFER', 5.00, 'BRL', '2026-11-03',
                                :from, :to, :membershipId, :membershipId)""")
                .param("id", TestIds.v7())
                .param("workspaceId", workspaceId)
                .param("from", ledger.walletOf(workspaceId))
                .param("to", bank)
                .param("membershipId", membershipId)
                .update();

        var accounts = accounts(userId);
        var dashboard = dashboard(userId, "2026-11");

        assertThat(accounts).bodyJson().isLenientlyEqualTo("""
                [{"name":"Banco","balance":{"amount":"5.00","currency":"BRL"}},
                 {"name":"Carteira","balance":{"amount":"-5.00","currency":"BRL"}}]""");
        assertThat(dashboard).bodyJson().extractingPath("$.expenses.amount").isEqualTo("0.00");
        assertThat(dashboard).bodyJson().extractingPath("$.balance.amount").isEqualTo("0.00");
    }

    /** PRD section 7.1: 1000 in the checking account, 5000 in, 1234.56 out and 500 moved to savings. */
    @Test
    void prdExampleThroughTheApi() {
        UUID checking = account("Corrente", "CHECKING", "BRL", "1000.00", false);
        UUID savings = account("Poupança", "SAVINGS", "BRL", "0.00", false);
        postTransaction("INCOME", checking, null, ledger.categoryOf(workspaceId, "INCOME", "Salário"), "5000.00", DAY);
        postTransaction("EXPENSE", checking, null, mercadoId, "1234.56", DAY);
        postTransaction("TRANSFER", checking, savings, null, "500.00", DAY);

        var dashboard = dashboard(userId, MONTH);
        var accounts = accounts(userId);

        assertThat(dashboard).hasStatusOk();
        assertThat(dashboard).bodyJson().isLenientlyEqualTo("""
                {"month":"2026-11",
                 "income":{"amount":"5000.00","currency":"BRL"},
                 "expenses":{"amount":"1234.56","currency":"BRL"},
                 "result":{"amount":"3765.44","currency":"BRL"},
                 "balance":{"amount":"4765.44","currency":"BRL"},
                 "byCategory":[{"categoryId":"%s",
                                "current":{"amount":"1234.56","currency":"BRL"},
                                "previous":{"amount":"0.00","currency":"BRL"}}]}""".formatted(mercadoId));
        assertThat(dashboard).bodyJson().extractingPath("$.byCategory").asList().hasSize(1);
        assertThat(dashboard)
                .bodyJson()
                .extractingPath("$.dailyBalance[29].balance.amount")
                .isEqualTo("4765.44");
        assertThat(dashboard)
                .bodyJson()
                .extractingPath("$.dailyBalance[3].balance.amount")
                .isEqualTo("1000.00");
        assertThat(accounts).bodyJson().isLenientlyEqualTo("""
                [{"name":"Carteira","balance":{"amount":"0.00","currency":"BRL"}},
                 {"name":"Corrente","balance":{"amount":"4265.44","currency":"BRL"}},
                 {"name":"Poupança","balance":{"amount":"500.00","currency":"BRL"}}]""");
    }

    /** Archiving an account takes its balance out of the family's; what it spent stays in the month's expenses. */
    @Test
    void archivedAccountLeavesBalance() {
        UUID bank = account("Banco", "CHECKING", "BRL", "250.00", false);
        postTransaction("EXPENSE", bank, null, mercadoId, "50.00", DAY);
        assertThat(dashboard(userId, MONTH))
                .bodyJson()
                .extractingPath("$.balance.amount")
                .isEqualTo("200.00");

        jdbc.sql("update accounts set archived_at = now() where id = :id")
                .param("id", bank)
                .update();
        var after = dashboard(userId, MONTH);

        assertThat(after).bodyJson().extractingPath("$.balance.amount").isEqualTo("0.00");
        assertThat(after).bodyJson().extractingPath("$.expenses.amount").isEqualTo("50.00");
    }

    @Test
    void pastMonthBalance() {
        ledger.expense(workspaceId, membershipId, mercadoId, "30.00", DAY);

        var october = dashboard(userId, "2026-10");

        assertThat(october).bodyJson().extractingPath("$.balance.amount").isEqualTo("0.00");
        assertThat(october).bodyJson().extractingPath("$.expenses.amount").isEqualTo("0.00");
        assertThat(dashboard(userId, MONTH))
                .bodyJson()
                .extractingPath("$.balance.amount")
                .isEqualTo("-30.00");
    }

    @Test
    void futureFlowsStayOut() {
        ledger.expense(workspaceId, membershipId, mercadoId, "30.00", LocalDate.of(2026, 12, 5));

        var result = dashboard(userId, MONTH);

        assertThat(result).bodyJson().extractingPath("$.balance.amount").isEqualTo("0.00");
        assertThat(result).bodyJson().extractingPath("$.expenses.amount").isEqualTo("0.00");
    }

    @Test
    void previousMonthInByCategory() {
        UUID lazer = ledger.categoryOf(workspaceId, "EXPENSE", "Lazer");
        ledger.expense(workspaceId, membershipId, lazer, "80.00", LocalDate.of(2026, 10, 15));
        ledger.expense(workspaceId, membershipId, lazer, "20.00", DAY);

        var result = dashboard(userId, MONTH);

        assertThat(result).bodyJson().extractingPath("$.byCategory").asList().hasSize(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.byCategory[0].categoryId")
                .isEqualTo(lazer.toString());
        assertThat(result)
                .bodyJson()
                .extractingPath("$.byCategory[0].current.amount")
                .isEqualTo("20.00");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.byCategory[0].previous.amount")
                .isEqualTo("80.00");
    }

    @Test
    void groupsBeyondSix() {
        List<UUID> categories = jdbc.sql("""
                        select id
                        from categories
                        where workspace_id = :workspaceId
                          and kind = 'EXPENSE'
                          and parent_id is null
                        order by name
                        limit 8""")
                .param("workspaceId", workspaceId)
                .query(UUID.class)
                .list();
        for (int i = 0; i < categories.size(); i++) {
            ledger.expense(workspaceId, membershipId, categories.get(i), (i + 1) + ".00", DAY);
        }

        var result = dashboard(userId, MONTH);

        assertThat(categories).hasSize(8);
        assertThat(result).bodyJson().extractingPath("$.byCategory").asList().hasSize(7);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.byCategory[6].categoryId")
                .isNull();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.byCategory[6].current.amount")
                .isEqualTo("3.00");
    }

    @Test
    void subcategoryFoldsIntoParent() {
        UUID feira = jdbc.sql("""
                        insert into categories (workspace_id, parent_id, kind, name)
                        values (:workspaceId, :parentId, 'EXPENSE', 'Feira')
                        returning id""")
                .param("workspaceId", workspaceId)
                .param("parentId", mercadoId)
                .query(UUID.class)
                .single();
        ledger.expense(workspaceId, membershipId, feira, "15.00", DAY);
        ledger.expense(workspaceId, membershipId, mercadoId, "5.00", DAY);

        var result = dashboard(userId, MONTH);

        assertThat(result).bodyJson().extractingPath("$.byCategory").asList().hasSize(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.byCategory[0].categoryId")
                .isEqualTo(mercadoId.toString());
        assertThat(result)
                .bodyJson()
                .extractingPath("$.byCategory[0].current.amount")
                .isEqualTo("20.00");
    }

    @Test
    void deletedTransactionsStayOut() {
        postIncome("100.00", "2026-11-05");
        ledger.expense(workspaceId, membershipId, mercadoId, "10.00", DAY);
        ledger.expense(workspaceId, membershipId, mercadoId, "10.00", LocalDate.of(2026, 10, 5));
        jdbc.sql("update transactions set deleted_at = now() where workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .update();

        var result = dashboard(userId, MONTH);

        assertThat(result).bodyJson().extractingPath("$.income.amount").isEqualTo("0.00");
        assertThat(result).bodyJson().extractingPath("$.expenses.amount").isEqualTo("0.00");
        assertThat(result).bodyJson().extractingPath("$.balance.amount").isEqualTo("0.00");
        assertThat(result).bodyJson().extractingPath("$.byCategory").asList().isEmpty();
    }

    @Test
    void dailyBalanceHasEveryDay() {
        var result = dashboard(userId, MONTH);

        assertThat(result).bodyJson().extractingPath("$.dailyBalance").asList().hasSize(30);
        assertThat(result).bodyJson().extractingPath("$.dailyBalance[0].day").isEqualTo("2026-11-01");
        assertThat(result).bodyJson().extractingPath("$.dailyBalance[29].day").isEqualTo("2026-11-30");
    }

    @Test
    void viewerCanRead() {
        UUID viewerId = users.create("Bia Teste", "bia@example.com");
        workspaces.addMember(workspaceId, viewerId, Role.VIEWER);

        assertThat(dashboard(viewerId, "2026-11")).hasStatusOk();
        assertThat(accounts(viewerId)).hasStatusOk();
    }

    /** The same month limits as the list: from January 2000 to the month a year and a day ahead. */
    @Test
    void monthOutOfRangeIs422() {
        for (String month : List.of("1999-12", "2999-01")) {
            var result = dashboard(userId, month);
            assertThat(result).as(month).hasStatus(422);
            assertThat(result).as(month).bodyJson().isLenientlyEqualTo("""
                    {"code":"month.out-of-range","detail":"Escolha um mês entre janeiro de 2000 e o próximo ano."}""");
        }
        assertThat(dashboard(userId, "2000-01")).hasStatusOk();
    }

    @Test
    void monthMustBeYearMonth() {
        var missing = mvc.get()
                .uri("/v1/workspaces/" + workspaceId + "/dashboard")
                .with(TestUsers.signedIn(userId, null))
                .exchange();
        var malformed = dashboard(userId, "nov-2026");

        assertThat(missing).hasStatus(400);
        assertThat(missing).bodyJson().extractingPath("$.code").isEqualTo("request.malformed");
        assertThat(malformed).hasStatus(400);
        assertThat(malformed).bodyJson().extractingPath("$.code").isEqualTo("request.malformed");
    }

    private UUID account(String name, String type, String currency, String initialBalance, boolean archived) {
        return jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance, archived_at)
                        values (:workspaceId, :name, :type, :currency, :initialBalance,
                                case when :archived then now() end)
                        returning id""")
                .param("workspaceId", workspaceId)
                .param("name", name)
                .param("type", type)
                .param("currency", currency)
                .param("initialBalance", new BigDecimal(initialBalance))
                .param("archived", archived)
                .query(UUID.class)
                .single();
    }

    private void postIncome(String amount, String occurredOn) {
        UUID id = TestIds.v7();
        var result = mvc.post()
                .uri("/v1/workspaces/" + workspaceId + "/transactions")
                .with(TestUsers.signedIn(userId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(userId, null)))
                .header("Idempotency-Key", id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id":"%s","kind":"INCOME","amount":{"amount":"%s","currency":"BRL"},"occurredOn":"%s",
                         "accountId":"%s","categoryId":"%s"}""".formatted(
                                id,
                                amount,
                                occurredOn,
                                ledger.walletOf(workspaceId),
                                ledger.categoryOf(workspaceId, "INCOME", "Salário")))
                .exchange();
        assertThat(result).hasStatus(201);
    }

    private void postTransaction(
            String kind, UUID accountId, UUID toAccountId, UUID categoryId, String amount, LocalDate on) {
        UUID id = TestIds.v7();
        var result = mvc.post()
                .uri("/v1/workspaces/" + workspaceId + "/transactions")
                .with(TestUsers.signedIn(userId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(userId, null)))
                .header("Idempotency-Key", id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id":"%s","kind":"%s","amount":{"amount":"%s","currency":"BRL"},"occurredOn":"%s",
                         "accountId":"%s","toAccountId":%s,"categoryId":%s}""".formatted(id, kind, amount, on, accountId, quoted(toAccountId), quoted(categoryId)))
                .exchange();
        assertThat(result).hasStatus(201);
    }

    private static String quoted(UUID id) {
        return id == null ? "null" : "\"" + id + "\"";
    }

    private MvcTestResult dashboard(UUID callerId, String month) {
        return mvc.get()
                .uri("/v1/workspaces/" + workspaceId + "/dashboard?month=" + month)
                .with(TestUsers.signedIn(callerId, null))
                .exchange();
    }

    private MvcTestResult accounts(UUID callerId) {
        return mvc.get()
                .uri("/v1/workspaces/" + workspaceId + "/accounts")
                .with(TestUsers.signedIn(callerId, null))
                .exchange();
    }
}
