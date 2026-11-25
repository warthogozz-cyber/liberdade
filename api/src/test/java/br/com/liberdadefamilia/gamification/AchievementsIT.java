package br.com.liberdadefamilia.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.gamification.domain.AchievementKey;
import br.com.liberdadefamilia.gamification.internal.AchievementRepository;
import br.com.liberdadefamilia.gamification.internal.AchievementService;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;

/** What the evaluation grants, called straight on the service. Today is Friday 2026-11-20, a week of 2026-11-16. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AchievementsIT.Config.class})
class AchievementsIT {

    private static final Set<AchievementKey> RESERVE =
            Set.of(AchievementKey.RESERVE_1_MONTH, AchievementKey.RESERVE_3_MONTHS, AchievementKey.RESERVE_6_MONTHS);

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }

        @Bean
        Recorded recorded() {
            return new Recorded();
        }
    }

    /** What the evaluation announced. */
    static class Recorded {

        private final List<AchievementsEarned> events = new CopyOnWriteArrayList<>();

        @EventListener
        void on(AchievementsEarned event) {
            events.add(event);
        }

        List<AchievementsEarned> events() {
            return events;
        }
    }

    @Autowired
    AchievementService service;

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
    Recorded recorded;

    private UUID ws;
    private UUID owner;
    private UUID adult;
    private UUID viewer;
    private UUID youth;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-20T12:00:00Z"));
        recorded.events().clear();
        UUID ownerUser = users.create("Ana Teste", "ana" + UUID.randomUUID() + "@example.com");
        ws = workspaces.createFor(ownerUser);
        owner = workspaces.membershipOf(ws, ownerUser);
        adult = workspaces.addMember(ws, users.create("Beto Teste", null), Role.ADULT);
        viewer = workspaces.addMember(ws, users.create("Vera Teste", null), Role.VIEWER);
        youth = workspaces.addMember(ws, users.create("Teo Teste", null), Role.YOUTH);
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    @DisplayName("primeiro lançamento")
    void firstTransaction() {
        enable("gamification");
        entry(owner, "2026-11-18T15:00:00Z", null);

        service.evaluate(ws, owner, false);

        assertThat(rows(owner))
                .extracting(AchievementRepository.Row::key)
                .containsExactly(AchievementKey.FIRST_TRANSACTION);
        assertThat(rows(owner)).allSatisfy(row -> assertThat(row.silent()).isFalse());
    }

    @Test
    @DisplayName("recorrência não dá o primeiro")
    void recurrenceDoesNotGiveTheFirst() {
        enable("gamification");
        entry(owner, "2026-11-18T15:00:00Z", rule());

        service.evaluate(ws, owner, false);

        assertThat(rows(owner)).isEmpty();
    }

    @Test
    @DisplayName("quatro semanas")
    void fourWeeks() {
        enable("gamification");
        for (String at : List.of(
                "2026-10-27T15:00:00Z", "2026-11-03T15:00:00Z", "2026-11-10T15:00:00Z", "2026-11-17T15:00:00Z")) {
            entry(owner, at, null);
        }

        service.evaluate(ws, owner, false);

        assertThat(rows(owner)).extracting(AchievementRepository.Row::key).contains(AchievementKey.STREAK_4_WEEKS);
    }

    @Test
    @DisplayName("jovem sem reserva")
    void youthHasNoReserve() {
        enable("gamification");
        enable("planning");
        reserveOfMonths(6);

        service.evaluateFamily(ws, false);

        for (UUID member : List.of(owner, adult, viewer)) {
            assertThat(keys(member)).containsAll(RESERVE);
        }
        assertThat(keys(youth)).doesNotContainAnyElementsOf(RESERVE);
    }

    @Test
    @DisplayName("meta alcançada para todos")
    void goalReachedForEveryone() {
        enable("gamification");
        enable("planning");
        goal(adult, true);

        service.evaluateFamily(ws, false);

        for (UUID member : List.of(owner, adult, viewer, youth)) {
            assertThat(keys(member)).contains(AchievementKey.GOAL_ACHIEVED);
        }
    }

    @Test
    @DisplayName("primeira meta só de quem criou")
    void firstGoalOnlyOfTheCreator() {
        enable("gamification");
        enable("planning");
        goal(adult, false);

        service.evaluateFamily(ws, false);

        assertThat(keys(adult)).contains(AchievementKey.FIRST_GOAL);
        for (UUID member : List.of(owner, viewer, youth)) {
            assertThat(keys(member)).doesNotContain(AchievementKey.FIRST_GOAL);
        }
    }

    @Test
    @DisplayName("planejar desligado")
    void planningOff() {
        enable("gamification");
        disable("planning");
        goal(adult, true);
        reserveOfMonths(6);

        service.evaluateFamily(ws, false);

        for (UUID member : List.of(owner, adult, viewer, youth)) {
            assertThat(keys(member)).noneMatch(AchievementKey::needsPlanning);
        }
    }

    @Test
    @DisplayName("módulo desligado")
    void moduleOff() {
        enable("planning");
        entry(owner, "2026-11-18T15:00:00Z", null);
        goal(adult, true);

        service.evaluateFamily(ws, false);

        assertThat(jdbc.sql("select count(*) from achievements where workspace_id = :ws")
                        .param("ws", ws)
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    @DisplayName("idempotente")
    void idempotent() {
        enable("gamification");
        entry(owner, "2026-11-18T15:00:00Z", null);
        service.evaluate(ws, owner, false);
        Instant first = rows(owner).get(0).earnedAt();

        clock.freeze(Instant.parse("2026-11-21T12:00:00Z"));
        service.evaluate(ws, owner, false);

        assertThat(rows(owner)).hasSize(1);
        assertThat(rows(owner).get(0).earnedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("evento só com linha nova")
    void eventOnlyWithANewRow() {
        enable("gamification");
        entry(owner, "2026-11-18T15:00:00Z", null);
        entry(adult, "2026-11-18T15:00:00Z", null);

        service.evaluate(ws, owner, false);
        assertThat(recorded.events()).containsExactly(new AchievementsEarned(ws, owner));

        service.evaluate(ws, owner, false);
        assertThat(recorded.events()).hasSize(1);

        service.evaluate(ws, adult, true);
        assertThat(recorded.events()).hasSize(1);
        assertThat(rows(adult)).hasSize(1);
        assertThat(rows(adult).get(0).silent()).isTrue();
    }

    @Test
    @DisplayName("mês no orçamento fora daqui")
    void monthInBudgetIsNotGrantedHere() {
        enable("gamification");
        enable("planning");
        jdbc.sql("""
                        insert into budgets (workspace_id, category_id, month, limit_amount, currency)
                        values (:ws, :category, date '2026-11-01', 100, 'BRL')""")
                .param("ws", ws)
                .param("category", ledger.categoryOf(ws, "EXPENSE", "Mercado"))
                .update();
        entry(owner, "2026-11-18T15:00:00Z", null);

        service.evaluate(ws, owner, false);
        service.evaluateFamily(ws, false);

        assertThat(jdbc.sql("select count(*) from achievements where achievement_key = 'MONTH_IN_BUDGET'"
                                + " and workspace_id = :ws")
                        .param("ws", ws)
                        .query(Long.class)
                        .single())
                .isZero();
    }

    private List<AchievementRepository.Row> rows(UUID membership) {
        return service.earned(ws, membership);
    }

    private Set<AchievementKey> keys(UUID membership) {
        return rows(membership).stream()
                .map(AchievementRepository.Row::key)
                .collect(java.util.stream.Collectors.toSet());
    }

    private void enable(String module) {
        jdbc.sql("""
                        insert into workspace_modules (workspace_id, module_key, enabled, updated_by)
                        select :ws, :key, true, user_id from memberships where id = :owner
                        on conflict (workspace_id, module_key) do update set enabled = true""").param("ws", ws).param("key", module).param("owner", owner).update();
    }

    private void disable(String module) {
        jdbc.sql("update workspace_modules set enabled = false where workspace_id = :ws and module_key = :key")
                .param("ws", ws)
                .param("key", module)
                .update();
    }

    /** A reserve account of {@code months} times a manual cost of 1000 a month. */
    private void reserveOfMonths(int months) {
        jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance, is_reserve)
                        values (:ws, 'Reserva', 'SAVINGS', 'BRL', :balance, true)""").param("ws", ws).param("balance", months * 1000).update();
        jdbc.sql("update workspaces set manual_monthly_cost = 1000 where id = :ws")
                .param("ws", ws)
                .update();
    }

    private void goal(UUID createdBy, boolean reached) {
        jdbc.sql("""
                        insert into goals (workspace_id, name, target_amount, currency, icon, created_by, achieved_at)
                        values (:ws, 'Viagem', 1000, 'BRL', 'plane', :by, case when :reached then now() end)""")
                .param("ws", ws)
                .param("by", createdBy)
                .param("reached", reached)
                .update();
    }

    private UUID rule() {
        return jdbc.sql("""
                        insert into recurring_rules
                            (workspace_id, kind, amount, currency, account_id, category_id, frequency, interval,
                             start_on)
                        values (:ws, 'EXPENSE', 10, 'BRL', :account, :category, 'MONTHLY', 1, '2026-11-05')
                        returning id""")
                .param("ws", ws)
                .param("account", ledger.walletOf(ws))
                .param("category", ledger.categoryOf(ws, "EXPENSE", "Mercado"))
                .query(UUID.class)
                .single();
    }

    private void entry(UUID createdBy, String createdAt, @Nullable UUID rule) {
        Timestamp at = Timestamp.from(Instant.parse(createdAt));
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on, account_id, category_id,
                             member_id, created_by, recurring_rule_id, scheduled_on, created_at)
                        values (:id, :ws, 'EXPENSE', 10, 'BRL', '2026-11-18', :account, :category,
                                :by, :by, :rule, :scheduled, :createdAt)""")
                .param("id", TestIds.v7())
                .param("ws", ws)
                .param("account", ledger.walletOf(ws))
                .param("category", ledger.categoryOf(ws, "EXPENSE", "Mercado"))
                .param("by", createdBy)
                .param("rule", rule)
                .param("scheduled", rule == null ? null : java.time.LocalDate.of(2026, 11, 18))
                .param("createdAt", at)
                .update();
    }
}
