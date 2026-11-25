package br.com.liberdadefamilia.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.gamification.internal.MonthInBudgetJob;
import br.com.liberdadefamilia.planning.PlanningFacts;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** The job of day one grants the month in budget. It runs at 2026-12-01 04:20 in Sao Paulo and reads November. */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MonthInBudgetJobIT.Config.class})
class MonthInBudgetJobIT {

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

    /** What the job announced. */
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
    MonthInBudgetJob job;

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

    @MockitoSpyBean
    PlanningFacts planningFacts;

    @BeforeEach
    void dayOne() {
        clock.freeze(Instant.parse("2026-12-01T07:20:00Z"));
        recorded.events().clear();
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    @DisplayName("mês dentro")
    void monthWithin() {
        Family family = family();
        budget(family, "2026-11-01", "100");
        ledger.expense(family.ws, family.owner, family.category, "40.00", LocalDate.of(2026, 11, 10));

        job.run();

        assertThat(silent(family.owner)).isFalse();
        assertThat(silent(family.adult)).isFalse();
        assertThat(silent(family.viewer)).isFalse();
        assertThat(count(family.youth)).isZero();
    }

    @Test
    @DisplayName("uma categoria acima")
    void oneCategoryOver() {
        Family family = family();
        budget(family, "2026-11-01", "100");
        ledger.expense(family.ws, family.owner, family.category, "150.00", LocalDate.of(2026, 11, 10));

        job.run();

        assertThat(count(family.owner) + count(family.adult) + count(family.viewer))
                .isZero();
        assertThat(recorded.events()).noneMatch(event -> event.workspaceId().equals(family.ws));
    }

    @Test
    @DisplayName("sem limite")
    void noLimit() {
        Family family = family();

        job.run();

        assertThat(count(family.owner)).isZero();
        assertThat(recorded.events()).noneMatch(event -> event.workspaceId().equals(family.ws));
    }

    @Test
    @DisplayName("módulo desligado")
    void moduleOff() {
        Family withoutAchievements = family();
        budget(withoutAchievements, "2026-11-01", "100");
        disable(withoutAchievements, "gamification");
        Family withoutPlanning = family();
        budget(withoutPlanning, "2026-11-01", "100");
        disable(withoutPlanning, "planning");

        job.run();

        assertThat(count(withoutAchievements.owner)).isZero();
        assertThat(count(withoutPlanning.owner)).isZero();
    }

    @Test
    @DisplayName("não retroativo")
    void notRetroactive() {
        Family family = family();
        budget(family, "2026-10-01", "100");
        ledger.expense(family.ws, family.owner, family.category, "40.00", LocalDate.of(2026, 10, 10));

        job.run();

        assertThat(count(family.owner) + count(family.adult) + count(family.viewer))
                .isZero();
    }

    @Test
    @DisplayName("uma família com erro não para as outras")
    void oneFamilyFailingDoesNotStopTheOthers(CapturedOutput output) {
        Family broken = family();
        budget(broken, "2026-11-01", "100");
        Family healthy = family();
        budget(healthy, "2026-11-01", "100");
        doThrow(new IllegalStateException("segredo-da-falha"))
                .when(planningFacts)
                .withinBudget(
                        argThat(member -> member != null && member.workspaceId().equals(broken.ws)), any());

        job.run();

        assertThat(count(broken.owner)).isZero();
        assertThat(count(healthy.owner)).isEqualTo(1);
        assertThat(output.getAll())
                .contains("month in budget job failed")
                .contains("IllegalStateException")
                .doesNotContain("segredo-da-falha");
    }

    @Test
    @DisplayName("aviso por membro")
    void oneNoticePerMember() {
        Family family = family();
        budget(family, "2026-11-01", "100");

        job.run();
        job.run();

        assertThat(recorded.events())
                .filteredOn(event -> event.workspaceId().equals(family.ws))
                .containsExactlyInAnyOrder(
                        new AchievementsEarned(family.ws, family.owner),
                        new AchievementsEarned(family.ws, family.adult),
                        new AchievementsEarned(family.ws, family.viewer));
    }

    private record Family(UUID ws, UUID owner, UUID adult, UUID viewer, UUID youth, UUID category) {}

    private Family family() {
        UUID ownerUser = users.create("Ana Teste", "ana" + UUID.randomUUID() + "@example.com");
        UUID ws = workspaces.createFor(ownerUser);
        UUID owner = workspaces.membershipOf(ws, ownerUser);
        UUID adult = workspaces.addMember(ws, users.create("Beto Teste", null), Role.ADULT);
        UUID viewer = workspaces.addMember(ws, users.create("Vera Teste", null), Role.VIEWER);
        UUID youth = workspaces.addMember(ws, users.create("Teo Teste", null), Role.YOUTH);
        Family family = new Family(ws, owner, adult, viewer, youth, ledger.categoryOf(ws, "EXPENSE", "Mercado"));
        enable(family, "gamification", ownerUser);
        enable(family, "planning", ownerUser);
        return family;
    }

    private void enable(Family family, String module, UUID user) {
        jdbc.sql("""
                        insert into workspace_modules (workspace_id, module_key, enabled, updated_by)
                        values (:ws, :key, true, :user)
                        on conflict (workspace_id, module_key) do update set enabled = true""")
                .param("ws", family.ws)
                .param("key", module)
                .param("user", user)
                .update();
    }

    private void disable(Family family, String module) {
        jdbc.sql("update workspace_modules set enabled = false where workspace_id = :ws and module_key = :key")
                .param("ws", family.ws)
                .param("key", module)
                .update();
    }

    private void budget(Family family, String month, String limit) {
        jdbc.sql("""
                        insert into budgets (workspace_id, category_id, month, limit_amount, currency)
                        values (:ws, :category, :month, :limit::numeric, 'BRL')""")
                .param("ws", family.ws)
                .param("category", family.category)
                .param("month", LocalDate.parse(month))
                .param("limit", limit)
                .update();
    }

    private long count(UUID membership) {
        return jdbc.sql("select count(*) from achievements where membership_id = :m"
                        + " and achievement_key = 'MONTH_IN_BUDGET'")
                .param("m", membership)
                .query(Long.class)
                .single();
    }

    private boolean silent(UUID membership) {
        return jdbc.sql("select silent from achievements where membership_id = :m"
                        + " and achievement_key = 'MONTH_IN_BUDGET'")
                .param("m", membership)
                .query(Boolean.class)
                .single();
    }
}
