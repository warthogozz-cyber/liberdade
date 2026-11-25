package br.com.liberdadefamilia.planning;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Optional;
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
import org.springframework.jdbc.core.simple.JdbcClient;

/** The planning facts the achievements read. Today is 2026-11-20. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PlanningFactsIT.Config.class})
class PlanningFactsIT {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    @Autowired
    PlanningFacts facts;

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

    private UUID ws;
    private UUID ownerMembership;
    private UUID adultMembership;
    private WorkspaceMember owner;
    private UUID mercado;
    private UUID lazer;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-20T12:00:00Z"));
        UUID ownerUser = users.create("Ana Teste", "ana@example.com");
        UUID adultUser = users.create("Beto Teste", "beto@example.com");
        ws = workspaces.createFor(ownerUser);
        ownerMembership = workspaces.membershipOf(ws, ownerUser);
        adultMembership = workspaces.addMember(ws, adultUser, Role.ADULT);
        owner = new WorkspaceMember(
                ws,
                ownerMembership,
                ownerUser,
                Role.OWNER,
                ZoneId.of("America/Sao_Paulo"),
                Currency.getInstance("BRL"));
        mercado = ledger.categoryOf(ws, "EXPENSE", "Mercado");
        lazer = ledger.categoryOf(ws, "EXPENSE", "Lazer");
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void goalCreatedByMe() {
        goal(adultMembership, false, false);

        assertThat(facts.createdAGoal(ws, adultMembership)).isTrue();
        assertThat(facts.createdAGoal(ws, ownerMembership)).isFalse();
    }

    @Test
    void archivedGoalStillCounts() {
        goal(adultMembership, true, false);

        assertThat(facts.createdAGoal(ws, adultMembership)).isTrue();
    }

    @Test
    void goalReached() {
        goal(adultMembership, false, true);
        assertThat(facts.anyGoalReached(ws)).isTrue();

        jdbc.sql("update goals set archived_at = now() where workspace_id = :ws")
                .param("ws", ws)
                .update();
        assertThat(facts.anyGoalReached(ws)).isTrue();
    }

    @Test
    void noGoalReached() {
        goal(adultMembership, false, false);

        assertThat(facts.anyGoalReached(ws)).isFalse();
    }

    @Test
    void reserveMonths() {
        reserveAccount("15300.00");
        ledger.expense(ws, ownerMembership, mercado, "4000.00", LocalDate.of(2026, 8, 10));
        ledger.expense(ws, ownerMembership, mercado, "4500.00", LocalDate.of(2026, 9, 10));
        ledger.expense(ws, ownerMembership, mercado, "5000.00", LocalDate.of(2026, 10, 10));

        Optional<BigDecimal> months = facts.reserveMonths(owner);

        assertThat(months).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("3.4"));
    }

    @Test
    void reserveWithoutCost() {
        reserveAccount("15300.00");

        assertThat(facts.reserveMonths(owner)).isEmpty();
    }

    @Test
    void noBudgetInTheMonth() {
        assertThat(facts.withinBudget(owner, NOVEMBER)).isEmpty();
    }

    @Test
    void atTheLimitCountsAsWithin() {
        budget(mercado, "100.00", LocalDate.of(2026, 11, 1));
        budget(lazer, "100.00", LocalDate.of(2026, 11, 1));
        ledger.expense(ws, ownerMembership, mercado, "100.00", LocalDate.of(2026, 11, 3));
        ledger.expense(ws, ownerMembership, lazer, "10.00", LocalDate.of(2026, 11, 3));

        assertThat(facts.withinBudget(owner, NOVEMBER)).contains(true);
    }

    @Test
    void oneOverIsNotWithin() {
        budget(mercado, "100.00", LocalDate.of(2026, 11, 1));
        budget(lazer, "100.00", LocalDate.of(2026, 11, 1));
        ledger.expense(ws, ownerMembership, mercado, "100.01", LocalDate.of(2026, 11, 3));

        assertThat(facts.withinBudget(owner, NOVEMBER)).contains(false);
    }

    @Test
    void familiesWithBudgets() {
        UUID otherUser = users.create("Caio Teste", "caio@example.com");
        UUID otherWs = workspaces.createFor(otherUser);
        budget(mercado, "100.00", LocalDate.of(2026, 11, 1));

        assertThat(facts.familiesWithBudgets(NOVEMBER)).contains(ws).doesNotContain(otherWs);
        assertThat(facts.familiesWithBudgets(YearMonth.of(2026, 10))).doesNotContain(ws);
    }

    private void goal(UUID createdBy, boolean archived, boolean reached) {
        jdbc.sql("""
                        insert into goals (workspace_id, name, target_amount, currency, icon, created_by,
                                           archived_at, achieved_at)
                        values (:ws, 'Viagem', 1000, 'BRL', 'plane', :by,
                                case when :archived then now() end, case when :reached then now() end)""")
                .param("ws", ws)
                .param("by", createdBy)
                .param("archived", archived)
                .param("reached", reached)
                .update();
    }

    private void budget(UUID category, String limit, LocalDate month) {
        jdbc.sql("""
                        insert into budgets (workspace_id, category_id, month, limit_amount, currency)
                        values (:ws, :category, :month, :limit::numeric, 'BRL')""")
                .param("ws", ws)
                .param("category", category)
                .param("month", month)
                .param("limit", limit)
                .update();
    }

    private void reserveAccount(String initialBalance) {
        jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance, is_reserve)
                        values (:ws, 'Reserva', 'SAVINGS', 'BRL', :balance::numeric, true)""").param("ws", ws).param("balance", initialBalance).update();
    }
}
