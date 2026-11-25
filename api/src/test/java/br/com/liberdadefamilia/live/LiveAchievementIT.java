package br.com.liberdadefamilia.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.gamification.AchievementsEarned;
import br.com.liberdadefamilia.live.internal.LiveStreams;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.Eventually;
import br.com.liberdadefamilia.support.SseStreams;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.MemberProfileChanged;
import br.com.liberdadefamilia.workspace.Role;
import java.time.Duration;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;

/** The achievement notice reaches only the person who earned it, and only while Conquistas is on. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, LiveAchievementIT.Config.class})
class LiveAchievementIT {

    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final String ACHIEVEMENT = "{\"entity\":\"achievement\"}";

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    @Autowired
    MockMvc mockMvc;

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
    LiveStreams streams;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    private UUID ana;
    private UUID bia;
    private UUID ws;
    private UUID anaMembership;
    private CsrfSession anaSession;
    private CsrfSession biaSession;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-20T12:00:00Z"));
        ana = users.create("Ana Teste", "ana" + UUID.randomUUID() + "@example.com");
        bia = users.create("Bia Teste", "bia" + UUID.randomUUID() + "@example.com");
        ws = workspaces.createFor(ana);
        anaMembership = workspaces.membershipOf(ws, ana);
        workspaces.addMember(ws, bia, Role.ADULT, "Bia");
        anaSession = CsrfSession.start(mvc, TestUsers.signedIn(ana, null));
        biaSession = CsrfSession.start(mvc, TestUsers.signedIn(bia, null));
    }

    @AfterEach
    void closeEverything() {
        clock.release();
        streams.all().forEach(streams::close);
    }

    private void gamification(boolean enabled) {
        jdbc.sql("""
                        insert into workspace_modules (workspace_id, module_key, enabled, updated_by)
                        values (:ws, 'gamification', :enabled, :ana)
                        on conflict (workspace_id, module_key) do update set enabled = :enabled""")
                .param("ws", ws)
                .param("enabled", enabled)
                .param("ana", ana)
                .update();
    }

    /** A notice every stream receives, published last, so that what is not there by then was not sent. */
    private void marker(MvcResult... receivers) {
        tx.executeWithoutResult(status -> events.publishEvent(new MemberProfileChanged(ws, TestIds.v7())));
        Eventually.within(WAIT, () -> {
            for (MvcResult receiver : receivers) {
                assertThat(SseStreams.changes(receiver)).anyMatch(change -> change.contains("\"members\""));
            }
        });
    }

    @Test
    @DisplayName("aviso só para quem ganhou")
    void onlyTheEarnerIsTold() throws Exception {
        gamification(true);
        MvcResult owner = SseStreams.open(mockMvc, ws, anaSession, TestUsers.signedIn(ana, null));
        MvcResult adult = SseStreams.open(mockMvc, ws, biaSession, TestUsers.signedIn(bia, null));
        UUID id = TestIds.v7();

        MvcResult posted = mockMvc.perform(post("/v1/workspaces/" + ws + "/transactions")
                        .with(TestUsers.signedIn(ana, null))
                        .with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)))
                        .header("Idempotency-Key", id.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","kind":"EXPENSE","amount":{"amount":"12.34","currency":"BRL"},
                                 "occurredOn":"2026-11-20","accountId":"%s","categoryId":"%s"}""".formatted(id, ledger.walletOf(ws), ledger.categoryOf(ws, "EXPENSE", "Mercado"))))
                .andReturn();
        assertThat(posted.getResponse().getStatus()).isEqualTo(201);

        Eventually.within(WAIT, () -> assertThat(SseStreams.changes(owner)).contains(ACHIEVEMENT));
        marker(owner, adult);
        assertThat(SseStreams.changes(adult)).doesNotContain(ACHIEVEMENT);
    }

    @Test
    @DisplayName("sem aviso com o módulo desligado")
    void moduleOffSendsNothing() throws Exception {
        MvcResult owner = SseStreams.open(mockMvc, ws, anaSession, TestUsers.signedIn(ana, null));

        gamification(false);
        tx.executeWithoutResult(status -> events.publishEvent(new AchievementsEarned(ws, anaMembership)));
        marker(owner);
        assertThat(SseStreams.changes(owner)).doesNotContain(ACHIEVEMENT);

        // The same publication reaches the stream once the module is on.
        gamification(true);
        tx.executeWithoutResult(status -> events.publishEvent(new AchievementsEarned(ws, anaMembership)));
        Eventually.within(WAIT, () -> assertThat(SseStreams.changes(owner)).contains(ACHIEVEMENT));
    }
}
