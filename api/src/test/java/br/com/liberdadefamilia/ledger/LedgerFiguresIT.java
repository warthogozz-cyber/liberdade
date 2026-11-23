package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The figures the planning module reads from the ledger: confirmed money only, and only the family's own. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LedgerFiguresIT {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);
    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate NOV_30 = LocalDate.of(2026, 11, 30);

    @Autowired
    LedgerFigures figures;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestLedger ledger;

    @Test
    void expenseByCategoryCountsConfirmedOnly() {
        Family a = family();
        UUID mercado = category(a, "Mercado");
        UUID moradia = category(a, "Moradia");
        UUID aluguel = child(a, moradia, "Aluguel");
        expense(a, mercado, "100", day(10));
        planned(a, mercado, "50", day(12), null);
        deleted(a, mercado, "30", day(11));
        expense(a, mercado, "999", LocalDate.of(2026, 10, 31));
        expense(a, aluguel, "40", day(5));

        List<CategorySpending> rows = figures.confirmedExpenseByCategory(a.workspaceId(), NOV_1, NOV_30);

        assertThat(rows).hasSize(2);
        CategorySpending market = spendingOf(rows, mercado);
        assertThat(market.parentId()).isNull();
        assertThat(market.amount()).isEqualByComparingTo("100");
        CategorySpending rent = spendingOf(rows, aluguel);
        assertThat(rent.parentId()).isEqualTo(moradia);
        assertThat(rent.amount()).isEqualByComparingTo("40");
    }

    @Test
    void balancesRespectDateAndArchive() {
        Family a = family();
        UUID banco = account(a, "Banco", false, false);
        UUID antiga = account(a, "Antiga", true, false);
        income(a, banco, "1000", day(20));
        income(a, banco, "200", day(24));
        insert(a, "INCOME", "999", day(22), banco, null, salary(a), "PLANNED", null, false);
        income(a, antiga, "500", day(1));

        assertThat(figures.activeBalanceOn(a.workspaceId(), TODAY)).isEqualByComparingTo("1000");
        assertThat(figures.accountBalance(a.workspaceId(), banco)).isEqualByComparingTo("1200");
        assertThat(figures.accountBalance(a.workspaceId(), antiga)).isEqualByComparingTo("500");
    }

    @Test
    void reserveBalanceOnlyMarkedAccounts() {
        Family a = family();
        UUID reserva = account(a, "Reserva", false, true);
        income(a, reserva, "18000", day(1));
        income(a, a.wallet(), "500", day(1));

        assertThat(figures.reserveBalanceOn(a.workspaceId(), TODAY)).isEqualByComparingTo("18000");
        assertThat(figures.activeBalanceOn(a.workspaceId(), TODAY)).isEqualByComparingTo("18500");
    }

    @Test
    void monthTotalsWithoutRule() {
        Family a = family();
        UUID mercado = category(a, "Mercado");
        UUID moradia = category(a, "Moradia");
        UUID rule = rule(a, moradia);
        UUID banco = account(a, "Banco", false, false);
        insert(a, "EXPENSE", "2000", day(5), a.wallet(), null, moradia, "CONFIRMED", rule, false);
        expense(a, mercado, "300", day(10));
        insert(a, "TRANSFER", "700", day(12), a.wallet(), banco, null, "CONFIRMED", null, false);
        expense(a, mercado, "50", LocalDate.of(2026, 10, 15));

        List<MonthTotal> withoutRule =
                figures.confirmedExpenseByMonth(a.workspaceId(), YearMonth.of(2026, 10), YearMonth.of(2026, 11), true);
        List<MonthTotal> all =
                figures.confirmedExpenseByMonth(a.workspaceId(), YearMonth.of(2026, 10), YearMonth.of(2026, 11), false);

        assertThat(withoutRule)
                .extracting(MonthTotal::month)
                .containsExactly(YearMonth.of(2026, 10), YearMonth.of(2026, 11));
        assertThat(withoutRule.get(0).amount()).isEqualByComparingTo("50");
        assertThat(withoutRule.get(1).amount()).isEqualByComparingTo("300");
        assertThat(all.get(1).amount()).isEqualByComparingTo("2300");
    }

    @Test
    void splitUsesRuleAndParent() {
        Family a = family();
        UUID mercado = category(a, "Mercado");
        UUID moradia = category(a, "Moradia");
        jdbc.sql("update categories set is_fixed = true where id = :id")
                .param("id", moradia)
                .update();
        UUID condominio = child(a, moradia, "Condomínio");
        UUID rule = rule(a, mercado);
        insert(a, "EXPENSE", "2000", day(5), a.wallet(), null, mercado, "CONFIRMED", rule, false);
        expense(a, condominio, "150", day(10));
        expense(a, category(a, "Lazer"), "80", day(11));
        insert(a, "INCOME", "5000", day(5), a.wallet(), null, salary(a), "CONFIRMED", null, false);

        SpendingSplit split = figures.fixedAndVariable(a.workspaceId(), NOV_1, NOV_30);

        assertThat(split.fixed()).isEqualByComparingTo("2150");
        assertThat(split.variable()).isEqualByComparingTo("80");
    }

    @Test
    void openFlowsIncludeOverdueAndFutureConfirmed() {
        Family a = family();
        UUID mercado = category(a, "Mercado");
        planned(a, mercado, "90", day(22), null);
        planned(a, mercado, "120", day(25), null);
        insert(a, "INCOME", "3000", day(28), a.wallet(), null, salary(a), "CONFIRMED", null, false);
        insert(a, "EXPENSE", "70", day(26), a.wallet(), null, mercado, "PLANNED", null, true);
        expense(a, mercado, "10", TODAY);
        planned(a, mercado, "15", LocalDate.of(2026, 12, 5), null);

        List<ScheduledFlow> flows = figures.openFlowsAfter(a.workspaceId(), TODAY, NOV_30);

        assertThat(flows).extracting(ScheduledFlow::on).containsExactly(day(22), day(25), day(28));
        assertThat(flows)
                .extracting(ScheduledFlow::kind)
                .containsExactly(TransactionKind.EXPENSE, TransactionKind.EXPENSE, TransactionKind.INCOME);
        assertThat(flows.get(2).amount()).isEqualByComparingTo("3000");
    }

    @Test
    void occupiedDatesIncludeDeleted() {
        Family a = family();
        UUID mercado = category(a, "Mercado");
        UUID rule = rule(a, mercado);
        UUID other = rule(a, mercado);
        UUID skipped = planned(a, mercado, "90", LocalDate.of(2026, 11, 25), rule);
        jdbc.sql("update transactions set deleted_at = now() where id = :id")
                .param("id", skipped)
                .update();
        UUID confirmed = planned(a, mercado, "90", LocalDate.of(2026, 12, 25), rule);
        jdbc.sql("update transactions set status = 'CONFIRMED', occurred_on = '2026-12-24' where id = :id")
                .param("id", confirmed)
                .update();
        planned(a, mercado, "90", LocalDate.of(2026, 11, 30), other);

        assertThat(figures.occupiedDates(a.workspaceId(), rule))
                .containsExactlyInAnyOrder(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 12, 25));
    }

    @Test
    void otherFamilyIsInvisible() {
        Family a = family();
        Family b = family();
        UUID mercado = category(a, "Mercado");
        UUID rule = rule(a, mercado);
        UUID reserva = account(a, "Reserva", false, true);
        income(a, reserva, "18000", day(1));
        expense(a, mercado, "100", day(10));
        insert(a, "EXPENSE", "2000", day(5), a.wallet(), null, mercado, "CONFIRMED", rule, false);
        planned(a, mercado, "90", day(25), rule);
        UUID ws = b.workspaceId();

        assertThat(figures.category(ws, mercado)).isEmpty();
        assertThat(figures.account(ws, reserva)).isEmpty();
        assertThat(figures.accountBalance(ws, reserva)).isEqualByComparingTo("0");
        assertThat(figures.confirmedExpenseByCategory(ws, NOV_1, NOV_30)).isEmpty();
        assertThat(figures.activeBalanceOn(ws, TODAY)).isEqualByComparingTo("0");
        assertThat(figures.reserveBalanceOn(ws, TODAY)).isEqualByComparingTo("0");
        assertThat(figures.confirmedExpenseByMonth(ws, YearMonth.of(2026, 1), YearMonth.of(2026, 12), false))
                .isEmpty();
        assertThat(figures.firstConfirmedOn(ws)).isEmpty();
        SpendingSplit split = figures.fixedAndVariable(ws, NOV_1, NOV_30);
        assertThat(split.fixed()).isEqualByComparingTo("0");
        assertThat(split.variable()).isEqualByComparingTo("0");
        assertThat(figures.openFlowsAfter(ws, TODAY, NOV_30)).isEmpty();
        assertThat(figures.occupiedDates(ws, rule)).isEmpty();
        // The family itself sees all of it.
        assertThat(figures.category(a.workspaceId(), mercado))
                .contains(new CategoryRef(mercado, null, TransactionKind.EXPENSE, false));
        assertThat(figures.account(a.workspaceId(), reserva)).contains(new AccountRef(reserva, "BRL", false));
        assertThat(figures.firstConfirmedOn(a.workspaceId())).contains(day(1));
        assertThat(figures.occupiedDates(a.workspaceId(), rule)).containsExactlyInAnyOrder(day(5), day(25));
    }

    private static LocalDate day(int dayOfNovember) {
        return LocalDate.of(2026, 11, dayOfNovember);
    }

    private static CategorySpending spendingOf(List<CategorySpending> rows, UUID categoryId) {
        return rows.stream()
                .filter(row -> row.categoryId().equals(categoryId))
                .findFirst()
                .orElseThrow();
    }

    private void expense(Family f, UUID category, String amount, LocalDate on) {
        insert(f, "EXPENSE", amount, on, f.wallet(), null, category, "CONFIRMED", null, false);
    }

    private void deleted(Family f, UUID category, String amount, LocalDate on) {
        insert(f, "EXPENSE", amount, on, f.wallet(), null, category, "CONFIRMED", null, true);
    }

    private UUID planned(Family f, UUID category, String amount, LocalDate on, @Nullable UUID rule) {
        return insert(f, "EXPENSE", amount, on, f.wallet(), null, category, "PLANNED", rule, false);
    }

    private void income(Family f, UUID account, String amount, LocalDate on) {
        insert(f, "INCOME", amount, on, account, null, salary(f), "CONFIRMED", null, false);
    }

    private UUID insert(
            Family f,
            String kind,
            String amount,
            LocalDate on,
            UUID account,
            @Nullable UUID toAccount,
            @Nullable UUID category,
            String status,
            @Nullable UUID rule,
            boolean deleted) {
        UUID id = TestIds.v7();
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on, account_id, to_account_id,
                             category_id, member_id, created_by, status, recurring_rule_id, scheduled_on, deleted_at)
                        values (:id, :ws, :kind, cast(:amount as numeric), 'BRL', :on, :account, :toAccount,
                                :category, :member, :member, :status, :rule,
                                case when cast(:rule as uuid) is not null then cast(:on as date) end,
                                case when :deleted then now() end)""")
                .param("id", id)
                .param("ws", f.workspaceId())
                .param("kind", kind)
                .param("amount", amount)
                .param("on", on)
                .param("account", account)
                .param("toAccount", toAccount)
                .param("category", category)
                .param("member", f.membershipId())
                .param("status", status)
                .param("rule", rule)
                .param("deleted", deleted)
                .update();
        return id;
    }

    private UUID account(Family f, String name, boolean archived, boolean reserve) {
        return jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, archived_at, is_reserve)
                        values (:ws, :name, 'CHECKING', 'BRL', case when :archived then now() end, :reserve)
                        returning id""")
                .param("ws", f.workspaceId())
                .param("name", name)
                .param("archived", archived)
                .param("reserve", reserve)
                .query(UUID.class)
                .single();
    }

    private UUID child(Family f, UUID parent, String name) {
        return jdbc.sql("""
                        insert into categories (workspace_id, parent_id, kind, name)
                        values (:ws, :parent, 'EXPENSE', :name)
                        returning id""")
                .param("ws", f.workspaceId())
                .param("parent", parent)
                .param("name", name)
                .query(UUID.class)
                .single();
    }

    private UUID rule(Family f, UUID category) {
        return jdbc.sql("""
                        insert into recurring_rules
                            (workspace_id, kind, amount, currency, account_id, category_id, frequency, interval,
                             start_on)
                        values (:ws, 'EXPENSE', 2000, 'BRL', :account, :category, 'MONTHLY', 1, '2026-11-05')
                        returning id""")
                .param("ws", f.workspaceId())
                .param("account", f.wallet())
                .param("category", category)
                .query(UUID.class)
                .single();
    }

    private Family family() {
        UUID userId = users.create("Ana Teste", "ana" + UUID.randomUUID() + "@example.com");
        UUID workspaceId = workspaces.createFor(userId);
        return new Family(workspaceId, workspaces.membershipOf(workspaceId, userId), ledger.walletOf(workspaceId));
    }

    private record Family(UUID workspaceId, UUID membershipId, UUID wallet) {}

    private UUID category(Family f, String name) {
        return ledger.categoryOf(f.workspaceId(), "EXPENSE", name);
    }

    private UUID salary(Family f) {
        return ledger.categoryOf(f.workspaceId(), "INCOME", "Salário");
    }
}
