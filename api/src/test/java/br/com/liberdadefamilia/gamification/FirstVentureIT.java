package br.com.liberdadefamilia.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.gamification.domain.AchievementKey;
import br.com.liberdadefamilia.gamification.internal.AchievementRepository;
import br.com.liberdadefamilia.gamification.internal.AchievementService;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The "Primeiro projeto de renda extra" achievement: starting a project awards whoever created it. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FirstVentureIT.Config.class})
class FirstVentureIT {

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
    AchievementService service;

    @Autowired
    TestClock clock;

    @Autowired
    TestLedger ledger;

    private UUID ownerUser;
    private UUID adultUser;
    private UUID ws;
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
        enable("ventures");
        enable("gamification");
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    @DisplayName("ativar dá a conquista a quem criou, sem silêncio")
    void activatingAwardsTheCreator() {
        UUID venture = create(adultUser);

        assertThat(status(adultUser, venture, 0, "ACTIVE")).hasStatus(200);

        assertThat(rows(adult))
                .filteredOn(row -> row.key() == AchievementKey.FIRST_VENTURE)
                .singleElement()
                .satisfies(row -> assertThat(row.silent()).isFalse());
        assertThat(keys(owner)).doesNotContain(AchievementKey.FIRST_VENTURE);
    }

    @Test
    @DisplayName("outra pessoa ativa: a conquista vai para quem criou")
    void someoneElseActivates() {
        UUID venture = create(adultUser);

        assertThat(status(ownerUser, venture, 0, "ACTIVE")).hasStatus(200);

        assertThat(keys(adult)).contains(AchievementKey.FIRST_VENTURE);
        assertThat(keys(owner)).doesNotContain(AchievementKey.FIRST_VENTURE);
    }

    @Test
    @DisplayName("reabrir não dá a conquista")
    void reopeningDoesNotAward() {
        UUID venture = insertVenture(adult, "CLOSED", "2026-11-01", "2026-11-10");

        assertThat(status(adultUser, venture, 0, "PAUSED")).hasStatus(200);
        assertThat(status(adultUser, venture, 1, "ACTIVE")).hasStatus(200);

        assertThat(keys(adult)).doesNotContain(AchievementKey.FIRST_VENTURE);
    }

    @Test
    @DisplayName("projeto encerrado direto do planejamento não dá a conquista, nem na avaliação ampla")
    void closedStraightFromPlanningDoesNotAward() {
        UUID venture = create(adultUser);
        assertThat(status(adultUser, venture, 0, "CLOSED")).hasStatus(200);
        UUID id = TestIds.v7();

        MvcTestResult posted = send(
                ownerUser,
                HttpMethod.POST,
                "/transactions",
                """
                {"id":"%s","kind":"EXPENSE","amount":{"amount":"12.34","currency":"BRL"},"occurredOn":"2026-11-20",
                 "accountId":"%s","categoryId":"%s"}""".formatted(id, ledger.walletOf(ws), ledger.categoryOf(ws, "EXPENSE", "Mercado")),
                id);
        assertThat(posted).hasStatus(201);
        MvcTestResult list = mvc.get()
                .uri("/v1/workspaces/" + ws + "/gamification/achievements")
                .with(TestUsers.signedIn(adultUser, null))
                .exchange();

        assertThat(list).hasStatusOk();
        assertThat(count()).isZero();
        assertThat(keys(adult)).doesNotContain(AchievementKey.FIRST_VENTURE);
    }

    @Test
    @DisplayName("ativar e encerrar em outro dia continua valendo na avaliação ampla")
    void activatedThenClosedLaterStillCounts() {
        insertVenture(adult, "CLOSED", "2026-11-01", "2026-11-10");

        service.evaluateFamily(ws, true);

        assertThat(keys(adult)).contains(AchievementKey.FIRST_VENTURE);
    }

    @Test
    @DisplayName("com a gamificação desligada nada é dado")
    void gamificationOffAwardsNothing() {
        disable("gamification");
        UUID venture = create(adultUser);

        assertThat(status(adultUser, venture, 0, "ACTIVE")).hasStatus(200);

        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("ligar a gamificação com projeto já ativo dá a conquista em silêncio")
    void turningGamificationOnIsSilent() {
        disable("gamification");
        insertVenture(adult, "ACTIVE", "2026-11-05", null);
        enable("gamification");

        service.evaluateFamily(ws, true);

        assertThat(rows(adult))
                .filteredOn(row -> row.key() == AchievementKey.FIRST_VENTURE)
                .singleElement()
                .satisfies(row -> assertThat(row.silent()).isTrue());
        assertThat(keys(owner)).doesNotContain(AchievementKey.FIRST_VENTURE);
    }

    @Test
    @DisplayName("quem criou e saiu não deixa a conquista para ninguém")
    void creatorWhoLeftGetsNothing() {
        UUID venture = create(adultUser);
        jdbc.sql("update ventures set created_by = null where id = :id")
                .param("id", venture)
                .update();

        assertThat(status(ownerUser, venture, 0, "ACTIVE")).hasStatus(200);
        service.evaluateFamily(ws, true);

        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("só uma vez")
    void onlyOnce() {
        UUID first = create(adultUser);
        assertThat(status(adultUser, first, 0, "ACTIVE")).hasStatus(200);
        long before = count();
        assertThat(before).isEqualTo(1);
        UUID second = insertVenture(adult, "PLANNING", null, null);

        assertThat(status(adultUser, second, 0, "ACTIVE")).hasStatus(200);

        assertThat(count()).isEqualTo(before);
    }

    @Test
    @DisplayName("a lista mostra o que leva à conquista")
    void listShowsWhatLeadsToIt() {
        MvcTestResult result = mvc.get()
                .uri("/v1/workspaces/" + ws + "/gamification/achievements")
                .with(TestUsers.signedIn(adultUser, null))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[?(@.key == 'FIRST_VENTURE')].earnedAt")
                .asArray()
                .hasSize(1)
                .containsOnlyNulls();
    }

    // --- helpers ---

    private long count() {
        return jdbc.sql("select count(*) from achievements where workspace_id = :ws"
                        + " and achievement_key = 'FIRST_VENTURE'")
                .param("ws", ws)
                .query(Long.class)
                .single();
    }

    private List<AchievementRepository.Row> rows(UUID membership) {
        return service.earned(ws, membership);
    }

    private List<AchievementKey> keys(UUID membership) {
        return rows(membership).stream().map(AchievementRepository.Row::key).toList();
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

    private UUID create(UUID user) {
        MvcTestResult result = send(user, HttpMethod.POST, "/ventures", "{\"name\":\"Projeto\",\"type\":\"OTHER\"}");
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(201);
        return UUID.fromString(JsonPath.read(body(result), "$.id"));
    }

    private UUID insertVenture(UUID createdBy, String status, @Nullable String startedOn, @Nullable String closedOn) {
        return jdbc.sql("""
                        insert into ventures (workspace_id, name, type, currency, created_by, status, started_on,
                                              closed_on)
                        values (:ws, 'Outro', 'OTHER', 'BRL', :by, :status, cast(:startedOn as date),
                                cast(:closedOn as date))
                        returning id""")
                .param("ws", ws)
                .param("by", createdBy)
                .param("status", status)
                .param("startedOn", startedOn)
                .param("closedOn", closedOn)
                .query(UUID.class)
                .single();
    }

    private MvcTestResult status(UUID user, UUID venture, long version, String status) {
        return send(
                user,
                HttpMethod.POST,
                "/ventures/" + venture + "/status",
                "{\"version\":%d,\"status\":\"%s\",\"on\":null}".formatted(version, status));
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult send(UUID user, HttpMethod method, String path, @Nullable String body) {
        return send(user, method, path, body, null);
    }

    private MvcTestResult send(
            UUID user, HttpMethod method, String path, @Nullable String body, @Nullable UUID idempotencyKey) {
        var request = mvc.method(method).uri("/v1/workspaces/" + ws + path).with(TestUsers.signedIn(user, null));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey.toString());
        }
        if (method != HttpMethod.GET) {
            request.with(CsrfSession.start(mvc, TestUsers.signedIn(user, null)));
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }
}
