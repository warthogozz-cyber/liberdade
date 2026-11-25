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
import java.util.UUID;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The two read routes. Today is Friday 2026-11-20, a week of 2026-11-16, unless a test moves the clock. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, GamificationControllerIT.Config.class})
class GamificationControllerIT {

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
    AchievementService service;

    private UUID ws;
    private UUID ownerUser;
    private UUID adultUser;
    private UUID youthUser;
    private UUID owner;
    private UUID adult;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-20T12:00:00Z"));
        ownerUser = users.create("Ana Teste", "ana" + UUID.randomUUID() + "@example.com");
        ws = workspaces.createFor(ownerUser);
        owner = workspaces.membershipOf(ws, ownerUser);
        adultUser = users.create("Beto Teste", null);
        adult = workspaces.addMember(ws, adultUser, Role.ADULT);
        youthUser = users.create("Teo Teste", null);
        workspaces.addMember(ws, youthUser, Role.YOUTH);
        workspaces.addMember(ws, users.create("Vera Teste", null), Role.VIEWER);
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    @DisplayName("sequência de quem chama")
    void streakOfTheCaller() {
        enable("gamification");
        entry(owner, "2026-11-10T15:00:00Z");
        entry(owner, "2026-11-17T15:00:00Z");

        MvcTestResult mine = get(ownerUser, "streak");
        MvcTestResult other = get(adultUser, "streak");

        assertThat(mine).hasStatusOk();
        assertThat(mine).bodyJson().extractingPath("$.weeks").isEqualTo(2);
        assertThat(other).hasStatusOk();
        assertThat(other).bodyJson().extractingPath("$.weeks").isEqualTo(0);
        assertThat(other).bodyJson().extractingPath("$.frozenWeeks").isEqualTo(0);
        assertThat(other).bodyJson().extractingPath("$.longest").isEqualTo(0);
    }

    @Test
    @DisplayName("semana pelo fuso da família")
    void weekByTheFamilyZone() {
        enable("gamification");
        jdbc.sql("update workspaces set timezone = 'America/Sao_Paulo' where id = :ws")
                .param("ws", ws)
                .update();
        // Sunday 23:30 in Sao Paulo is already Monday in UTC; a UTC reading would see two weeks here.
        entry(owner, "2026-11-16T12:00:00Z");
        clock.freeze(Instant.parse("2026-11-23T02:30:00Z"));
        entry(owner, "2026-11-23T02:30:00Z");

        MvcTestResult result = get(ownerUser, "streak");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.weeks").isEqualTo(1);
    }

    @Test
    @DisplayName("lista na ordem")
    void listInOrder() {
        enable("gamification");
        enable("planning");

        MvcTestResult result = get(ownerUser, "achievements");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[*].key")
                .asArray()
                .containsExactly(
                        "FIRST_TRANSACTION",
                        "STREAK_4_WEEKS",
                        "FIRST_GOAL",
                        "GOAL_ACHIEVED",
                        "RESERVE_1_MONTH",
                        "RESERVE_3_MONTHS",
                        "RESERVE_6_MONTHS",
                        "MONTH_IN_BUDGET");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[*].earnedAt")
                .asArray()
                .containsOnlyNulls();
    }

    @Test
    @DisplayName("primeira leitura grava em silêncio")
    void firstReadWritesInSilence() {
        entry(owner, "2026-11-18T15:00:00Z");
        enable("gamification");
        // Clears the rows (if a listener wrote any) so the first read finds none and has to write them.
        jdbc.sql("delete from achievements where workspace_id = :ws")
                .param("ws", ws)
                .update();

        MvcTestResult result = get(ownerUser, "achievements");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[0].key").isEqualTo("FIRST_TRANSACTION");
        assertThat(result).bodyJson().extractingPath("$.items[0].silent").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.items[0].earnedAt").isNotNull();
    }

    @Test
    @DisplayName("lista do jovem")
    void youthList() {
        enable("gamification");
        enable("planning");

        MvcTestResult result = get(youthUser, "achievements");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[*].key")
                .asArray()
                .containsExactly("FIRST_TRANSACTION", "STREAK_4_WEEKS", "GOAL_ACHIEVED");
    }

    @Test
    @DisplayName("lista do jovem não mostra reserva já ganha")
    void youthListHidesEarnedReserve() {
        enable("gamification");
        enable("planning");
        jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance, is_reserve)
                        values (:ws, 'Reserva', 'SAVINGS', 'BRL', 6000, true)""").param("ws", ws).update();
        jdbc.sql("update workspaces set manual_monthly_cost = 1000 where id = :ws")
                .param("ws", ws)
                .update();
        service.evaluate(ws, adult, false);
        assertThat(service.earned(ws, adult))
                .extracting(AchievementRepository.Row::key)
                .contains(AchievementKey.RESERVE_1_MONTH);
        jdbc.sql("update memberships set role = 'YOUTH' where id = :id")
                .param("id", adult)
                .update();

        MvcTestResult result = get(adultUser, "achievements");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[*].key")
                .asArray()
                .contains("FIRST_TRANSACTION")
                .noneMatch(key -> key.toString().startsWith("RESERVE_"));
    }

    @Test
    @DisplayName("planejar desligado esconde as não ganhas")
    void planningOffHidesTheUnearned() {
        enable("gamification");
        enable("planning");
        jdbc.sql("""
                        insert into goals (workspace_id, name, target_amount, currency, icon, created_by, achieved_at)
                        values (:ws, 'Viagem', 1000, 'BRL', 'plane', :by, now())""").param("ws", ws).param("by", adult).update();
        service.evaluate(ws, owner, false);
        disable("planning");

        MvcTestResult result = get(ownerUser, "achievements");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[*].key")
                .asArray()
                .containsExactly("FIRST_TRANSACTION", "STREAK_4_WEEKS", "GOAL_ACHIEVED");
        assertThat(result).bodyJson().extractingPath("$.items[2].earnedAt").isNotNull();
    }

    @Test
    @DisplayName("módulo desligado")
    void moduleOff() {
        for (String route : new String[] {"streak", "achievements"}) {
            MvcTestResult result = get(ownerUser, route);

            assertThat(result).hasStatus(403);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("module.disabled");
        }
    }

    private MvcTestResult get(UUID user, String route) {
        return mvc.get()
                .uri("/v1/workspaces/" + ws + "/gamification/" + route)
                .with(TestUsers.signedIn(user, null))
                .exchange();
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

    private void entry(UUID createdBy, String createdAt) {
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on, account_id, category_id,
                             member_id, created_by, created_at)
                        values (:id, :ws, 'EXPENSE', 10, 'BRL', '2026-11-18', :account, :category,
                                :by, :by, :createdAt)""")
                .param("id", TestIds.v7())
                .param("ws", ws)
                .param("account", ledger.walletOf(ws))
                .param("category", ledger.categoryOf(ws, "EXPENSE", "Mercado"))
                .param("by", createdBy)
                .param("createdAt", Timestamp.from(Instant.parse(createdAt)))
                .update();
    }
}
