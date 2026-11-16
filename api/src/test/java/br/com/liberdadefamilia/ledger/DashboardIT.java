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
        assertThat(november).bodyJson().isStrictlyEqualTo("""
                {"month":"2026-11",
                 "expenses":{"amount":"10.00","currency":"BRL"},
                 "balance":{"amount":"-30.00","currency":"BRL"}}""");
        assertThat(december).bodyJson().isStrictlyEqualTo("""
                {"month":"2026-12",
                 "expenses":{"amount":"20.00","currency":"BRL"},
                 "balance":{"amount":"-30.00","currency":"BRL"}}""");
    }

    @Test
    void incomeNotInExpenses() {
        postIncome("100.00", "2026-11-05");
        ledger.expense(workspaceId, membershipId, mercadoId, "30.00", LocalDate.of(2026, 11, 6));

        var result = dashboard(userId, "2026-11");

        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {"month":"2026-11",
                 "expenses":{"amount":"30.00","currency":"BRL"},
                 "balance":{"amount":"70.00","currency":"BRL"}}""");
    }

    @Test
    void emptyMonth() {
        var result = dashboard(userId, "2026-11");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {"month":"2026-11",
                 "expenses":{"amount":"0.00","currency":"BRL"},
                 "balance":{"amount":"0.00","currency":"BRL"}}""");
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
    void accountsByNameWithoutArchived() {
        account("Poupança", "SAVINGS", "BRL", "100.00", true);
        account("Banco", "CHECKING", "BRL", "25.00", false);
        account("Viagem", "CASH", "USD", "40.00", false);

        var accounts = accounts(userId);
        var dashboard = dashboard(userId, "2026-11");

        assertThat(accounts).bodyJson().isLenientlyEqualTo("""
                [{"name":"Banco","type":"CHECKING","balance":{"amount":"25.00","currency":"BRL"}},
                 {"name":"Carteira","type":"CASH","balance":{"amount":"0.00","currency":"BRL"}},
                 {"name":"Viagem","type":"CASH","currency":"USD","balance":{"amount":"40.00","currency":"USD"}}]""");
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

        assertThat(dashboard).bodyJson().isStrictlyEqualTo("""
                {"month":"2026-11",
                 "expenses":{"amount":"10.00","currency":"BRL"},
                 "balance":{"amount":"-10.00","currency":"BRL"}}""");
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

    @Test
    void viewerCanRead() {
        UUID viewerId = users.create("Bia Teste", "bia@example.com");
        workspaces.addMember(workspaceId, viewerId, Role.VIEWER);

        assertThat(dashboard(viewerId, "2026-11")).hasStatusOk();
        assertThat(accounts(viewerId)).hasStatusOk();
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
