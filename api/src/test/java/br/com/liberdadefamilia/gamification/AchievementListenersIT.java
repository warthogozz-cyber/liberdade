package br.com.liberdadefamilia.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.gamification.domain.AchievementKey;
import br.com.liberdadefamilia.planning.PlanningFacts;
import br.com.liberdadefamilia.privacy.internal.AccountPurgeJob;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The achievements granted by the real routes. Today is Friday 2026-11-20. */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AchievementListenersIT.Config.class})
class AchievementListenersIT {

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
    AccountPurgeJob purgeJob;

    @MockitoSpyBean
    PlanningFacts planningFacts;

    private UUID anaUser;
    private UUID ws;
    private UUID ana;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-20T12:00:00Z"));
        anaUser = users.create("Ana Teste", "ana" + UUID.randomUUID() + "@example.com");
        ws = workspaces.createFor(anaUser);
        ana = workspaces.membershipOf(ws, anaUser);
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    @DisplayName("lançar grava antes da resposta")
    void postingGrantsBeforeTheResponse() {
        enable("gamification");
        UUID id = TestIds.v7();

        MvcTestResult posted = send(
                anaUser,
                HttpMethod.POST,
                "/transactions",
                """
                {"id":"%s","kind":"EXPENSE","amount":{"amount":"12.34","currency":"BRL"},"occurredOn":"2026-11-20",
                 "accountId":"%s","categoryId":"%s"}""".formatted(id, ledger.walletOf(ws), ledger.categoryOf(ws, "EXPENSE", "Mercado")),
                id);

        assertThat(posted).hasStatus(201);
        assertThat(silent(ana, AchievementKey.FIRST_TRANSACTION)).isFalse();
    }

    @Test
    @DisplayName("ligar o módulo grava em silêncio")
    void turningTheModuleOnIsSilent() {
        ledger.expense(
                ws, ana, ledger.categoryOf(ws, "EXPENSE", "Mercado"), "10.00", java.time.LocalDate.of(2026, 11, 18));

        assertThat(send(anaUser, HttpMethod.PUT, "/modules/gamification", "{\"enabled\":true}", null))
                .hasStatusOk();

        assertThat(silent(ana, AchievementKey.FIRST_TRANSACTION)).isTrue();
    }

    @Test
    @DisplayName("ligar planejar grava em silêncio")
    void turningPlanningOnIsSilent() {
        enable("gamification");
        jdbc.sql("update workspace_modules set enabled = false where workspace_id = :ws and module_key = 'planning'")
                .param("ws", ws)
                .update();
        goal(ana, true);
        assertThat(keys(ana)).doesNotContain(AchievementKey.GOAL_ACHIEVED);

        assertThat(send(anaUser, HttpMethod.PUT, "/modules/planning", "{\"enabled\":true}", null))
                .hasStatusOk();

        assertThat(silent(ana, AchievementKey.GOAL_ACHIEVED)).isTrue();
    }

    @Test
    @DisplayName("membro novo grava em silêncio")
    void newMemberIsSilent() {
        enable("gamification");
        enable("planning");
        goal(ana, true);
        UUID biaUser = users.create("Bia Teste", "bia" + UUID.randomUUID() + "@example.com");
        MvcTestResult invitation =
                send(anaUser, HttpMethod.POST, "/invitations", "{\"role\":\"ADULT\",\"nickname\":\"Bibi\"}", null);
        assertThat(invitation).hasStatus(201);
        String token = JsonPath.read(body(invitation), "$.token");
        RequestPostProcessor session = TestUsers.signedIn(biaUser, null);

        MvcTestResult accepted = mvc.post()
                .uri("/v1/invitations/accept")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .with(request -> {
                    request.setRemoteAddr("10.9.8.7");
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}")
                .exchange();

        assertThat(accepted).hasStatusOk();
        UUID bia = workspaces.membershipOf(ws, biaUser);
        assertThat(silent(bia, AchievementKey.GOAL_ACHIEVED)).isTrue();
    }

    @Test
    @DisplayName("jovem que vira adulto")
    void youthWhoBecomesAnAdult() {
        enable("gamification");
        enable("planning");
        jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance, is_reserve)
                        values (:ws, 'Reserva', 'SAVINGS', 'BRL', 3000, true)""").param("ws", ws).update();
        jdbc.sql("update workspaces set manual_monthly_cost = 1000 where id = :ws")
                .param("ws", ws)
                .update();
        UUID teo = workspaces.addMember(ws, users.create("Teo Teste", null), Role.YOUTH);
        assertThat(keys(teo)).isEmpty();

        MvcTestResult promoted = send(anaUser, HttpMethod.PATCH, "/members/" + teo, "{\"role\":\"ADULT\"}", null);

        assertThat(promoted).hasStatusOk();
        assertThat(keys(teo))
                .contains(AchievementKey.RESERVE_1_MONTH, AchievementKey.RESERVE_3_MONTHS)
                .doesNotContain(AchievementKey.RESERVE_6_MONTHS);
        assertThat(silent(teo, AchievementKey.RESERVE_1_MONTH)).isTrue();
        assertThat(silent(teo, AchievementKey.RESERVE_3_MONTHS)).isTrue();
    }

    @Test
    @DisplayName("falha na avaliação não desfaz o lançamento")
    void evaluationFailureDoesNotUndoTheEntry(CapturedOutput output) {
        enable("gamification");
        enable("planning");
        doThrow(new IllegalStateException("segredo-da-falha"))
                .when(planningFacts)
                .reserveMonths(any());
        UUID id = TestIds.v7();

        MvcTestResult posted = send(
                anaUser,
                HttpMethod.POST,
                "/transactions",
                """
                {"id":"%s","kind":"EXPENSE","amount":{"amount":"12.34","currency":"BRL"},"occurredOn":"2026-11-20",
                 "accountId":"%s","categoryId":"%s"}""".formatted(id, ledger.walletOf(ws), ledger.categoryOf(ws, "EXPENSE", "Mercado")),
                id);

        assertThat(posted).hasStatus(201);
        assertThat(jdbc.sql("select count(*) from transactions where id = :id")
                        .param("id", id)
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
        verify(planningFacts).reserveMonths(any());
        assertThat(output.getAll())
                .contains("achievement evaluation failed")
                .contains("IllegalStateException")
                .doesNotContain("segredo-da-falha");
    }

    @Test
    @DisplayName("excluir a conta apaga as conquistas")
    void deletingTheAccountErasesTheAchievements() {
        enable("gamification");
        UUID biaUser = users.create("Bia Teste", "bia" + UUID.randomUUID() + "@example.com");
        UUID bia = workspaces.addMember(ws, biaUser, Role.ADULT);
        earn(bia);
        RequestPostProcessor session = TestUsers.signedIn(biaUser, null);
        assertThat(mvc.post()
                        .uri("/v1/me/deletion")
                        .with(session)
                        .with(CsrfSession.start(mvc, session))
                        .exchange())
                .hasStatus(202);
        assertThat(keys(bia)).isNotEmpty();

        clock.freeze(Instant.parse("2026-11-20T12:00:00Z").plus(Duration.ofDays(31)));
        purgeJob.purge();

        assertThat(keys(bia)).isEmpty();
    }

    @Test
    @DisplayName("remover da família apaga")
    void removingFromTheFamilyErases() {
        enable("gamification");
        UUID bia = workspaces.addMember(ws, users.create("Bia Teste", null), Role.ADULT);
        earn(bia);
        assertThat(keys(bia)).isNotEmpty();

        assertThat(send(anaUser, HttpMethod.DELETE, "/members/" + bia, null, null))
                .hasStatus(204);

        assertThat(keys(bia)).isEmpty();
    }

    private void earn(UUID membership) {
        jdbc.sql("""
                        insert into achievements (workspace_id, membership_id, achievement_key)
                        values (:ws, :m, 'FIRST_TRANSACTION')""").param("ws", ws).param("m", membership).update();
    }

    private List<AchievementKey> keys(UUID membership) {
        return jdbc.sql("select achievement_key from achievements where membership_id = :m order by achievement_key")
                .param("m", membership)
                .query((rs, row) -> AchievementKey.valueOf(rs.getString(1)))
                .list();
    }

    private boolean silent(UUID membership, AchievementKey key) {
        return jdbc.sql("select silent from achievements where membership_id = :m and achievement_key = :key")
                .param("m", membership)
                .param("key", key.name())
                .query(Boolean.class)
                .single();
    }

    private void enable(String module) {
        jdbc.sql("""
                        insert into workspace_modules (workspace_id, module_key, enabled, updated_by)
                        values (:ws, :key, true, :user)
                        on conflict (workspace_id, module_key) do update set enabled = true""")
                .param("ws", ws)
                .param("key", module)
                .param("user", anaUser)
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

    private MvcTestResult send(UUID user, HttpMethod method, String path, @Nullable String body, @Nullable UUID key) {
        var request = mvc.method(method)
                .uri("/v1/workspaces/" + ws + path)
                .with(TestUsers.signedIn(user, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(user, null)));
        if (key != null) {
            request.header("Idempotency-Key", key.toString());
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
