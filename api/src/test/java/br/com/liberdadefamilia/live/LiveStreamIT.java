package br.com.liberdadefamilia.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.live.internal.LiveHeartbeat;
import br.com.liberdadefamilia.live.internal.LiveStreams;
import br.com.liberdadefamilia.planning.GoalChanged;
import br.com.liberdadefamilia.planning.RecurringRuleCreated;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.Eventually;
import br.com.liberdadefamilia.support.SseStreams;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The family stream end to end: who gets which notice, what the notice carries, how many streams one person may hold
 * and how a stream ends. A family of an Owner, an Adult and a Youth; the stream of each opens with that person's
 * session, as the browser's does.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, LiveStreamIT.Config.class})
class LiveStreamIT {

    private static final Duration WAIT = Duration.ofSeconds(5);

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
    LiveHeartbeat heartbeat;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    private UUID ana;
    private UUID bia;
    private UUID leo;
    private UUID ws;
    private UUID biaMembership;
    private UUID leoMembership;
    private UUID wallet;
    private UUID mercado;
    private CsrfSession anaSession;
    private CsrfSession biaSession;
    private CsrfSession leoSession;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-20T12:00:00Z"));
        ana = users.create("Ana Teste", "ana@example.com");
        bia = users.create("Bia Teste", "bia@example.com");
        leo = users.create("Leo Teste", "leo@example.com");
        ws = workspaces.createFor(ana);
        biaMembership = workspaces.addMember(ws, bia, Role.ADULT, "Bia");
        leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        wallet = ledger.walletOf(ws);
        mercado = ledger.categoryOf(ws, "EXPENSE", "Mercado");
        anaSession = session(ana);
        biaSession = session(bia);
        leoSession = session(leo);
    }

    @AfterEach
    void closeEverything() {
        clock.release();
        streams.all().forEach(streams::close);
    }

    @Test
    void opensWithRetryAndComment() throws Exception {
        MvcResult stream = SseStreams.open(mockMvc, ws, anaSession, TestUsers.signedIn(ana, null));

        assertThat(stream.getRequest().isAsyncStarted()).isTrue();
        assertThat(stream.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(SseStreams.content(stream)).startsWith("retry: 5000\n").contains("\n: open\n\n");
    }

    @Test
    void adultOnlyAccountNeverReachesYouth() throws Exception {
        MvcResult adult = SseStreams.open(mockMvc, ws, biaSession, TestUsers.signedIn(bia, null));
        MvcResult youth = SseStreams.open(mockMvc, ws, leoSession, TestUsers.signedIn(leo, null));
        UUID bancoDaBia = account("Banco da Bia", biaMembership);

        UUID id = record(bancoDaBia, "10.00", null);

        Eventually.within(
                WAIT,
                () -> assertThat(SseStreams.changes(adult))
                        .contains("{\"entity\":\"transaction\",\"id\":\"" + id + "\"}"));
        marker(adult, youth);
        assertThat(SseStreams.changes(youth)).noneMatch(change -> change.contains("\"transaction\""));
    }

    @Test
    void sharedAccountReachesBoth() throws Exception {
        MvcResult adult = SseStreams.open(mockMvc, ws, biaSession, TestUsers.signedIn(bia, null));
        MvcResult youth = SseStreams.open(mockMvc, ws, leoSession, TestUsers.signedIn(leo, null));
        UUID leosWallet = account("Carteira do Leo", leoMembership);

        UUID id = record(leosWallet, "10.00", null);

        String expected = "{\"entity\":\"transaction\",\"id\":\"" + id + "\"}";
        Eventually.within(WAIT, () -> {
            assertThat(SseStreams.changes(adult)).contains(expected);
            assertThat(SseStreams.changes(youth)).contains(expected);
        });
    }

    @Test
    void formerOwnerIsTold() throws Exception {
        UUID leosWallet = account("Carteira do Leo", leoMembership);
        MvcResult youth = SseStreams.open(mockMvc, ws, leoSession, TestUsers.signedIn(leo, null));

        assertThat(send(
                                HttpMethod.PUT,
                                "/accounts/" + leosWallet + "/owner",
                                "{\"membershipId\":\"" + biaMembership + "\"}")
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);

        Eventually.within(
                WAIT,
                () -> assertThat(SseStreams.changes(youth))
                        .contains("{\"entity\":\"account\",\"id\":\"" + leosWallet + "\"}"));
    }

    @Test
    void youthNeverGetsBudgetRuleOrReserve() throws Exception {
        MvcResult adult = SseStreams.open(mockMvc, ws, biaSession, TestUsers.signedIn(bia, null));
        MvcResult youth = SseStreams.open(mockMvc, ws, leoSession, TestUsers.signedIn(leo, null));

        assertThat(send(HttpMethod.PUT, "/budgets/2026-11/" + mercado, """
                                {"limitAmount":{"amount":"100.00","currency":"BRL"},"version":null}""")
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
        assertThat(send(HttpMethod.POST, "/recurring-rules", """
                                {"kind":"EXPENSE","amount":{"amount":"30.00","currency":"BRL"},"accountId":"%s",
                                 "categoryId":"%s","frequency":"MONTHLY","interval":1,"startOn":"2026-12-05","endOn":null}""".formatted(wallet, mercado))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
        assertThat(send(HttpMethod.PATCH, "/reserve", "{\"targetMonths\":12}")
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);

        Eventually.within(WAIT, () -> {
            List<String> changes = SseStreams.changes(adult);
            assertThat(changes).anyMatch(change -> change.contains("\"budget\""));
            assertThat(changes).anyMatch(change -> change.contains("\"rule\""));
            assertThat(changes).anyMatch(change -> change.contains("\"reserve\""));
        });
        marker(adult, youth);
        assertThat(SseStreams.changes(youth))
                .noneMatch(change ->
                        change.contains("\"budget\"") || change.contains("\"rule\"") || change.contains("\"reserve\""));
    }

    @Test
    void planningOffSendsNoPlanningChanges() throws Exception {
        UUID goal = jdbc.sql("""
                        insert into goals (workspace_id, name, target_amount, currency, icon, created_by)
                        values (:ws, 'Viagem', 1000, 'BRL', 'travel', :by) returning id""")
                .param("ws", ws)
                .param("by", biaMembership)
                .query(UUID.class)
                .single();
        assertThat(send(HttpMethod.PUT, "/modules/planning", "{\"enabled\":false}")
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        MvcResult adult = SseStreams.open(mockMvc, ws, biaSession, TestUsers.signedIn(bia, null));

        // What the database or a job changes with the module off still raises the events.
        tx.executeWithoutResult(status -> {
            events.publishEvent(new GoalChanged(ws, goal));
            events.publishEvent(new RecurringRuleCreated(ws, TestIds.v7(), ana));
        });

        marker(adult);
        assertThat(SseStreams.changes(adult))
                .noneMatch(change -> change.contains("\"goal\"") || change.contains("\"rule\""));
    }

    @Test
    void noValuesInChanges() throws Exception {
        MvcResult adult = SseStreams.open(mockMvc, ws, biaSession, TestUsers.signedIn(bia, null));

        UUID id = record(wallet, "123.45", "Mercado");

        Eventually.within(
                WAIT,
                () -> assertThat(SseStreams.changes(adult))
                        .contains("{\"entity\":\"transaction\",\"id\":\"" + id + "\"}"));
        // Ids are random hex, which may spell "123" by chance; the notice itself must not.
        String withoutIds = SseStreams.content(adult)
                .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "ID");
        assertThat(withoutIds).doesNotContain("123").doesNotContain("Mercado");
    }

    @Test
    void otherFamilyIsNotFound() throws Exception {
        UUID dora = users.create("Dora Teste", "dora@example.com");
        UUID other = workspaces.createFor(dora);

        MvcResult result = SseStreams.open(mockMvc, other, anaSession, TestUsers.signedIn(ana, null));

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getRequest().isAsyncStarted()).isFalse();
    }

    @Test
    void sixthStreamIsRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(SseStreams.open(mockMvc, ws, anaSession, TestUsers.signedIn(ana, null))
                            .getRequest()
                            .isAsyncStarted())
                    .isTrue();
        }

        MvcResult sixth = SseStreams.open(mockMvc, ws, anaSession, TestUsers.signedIn(ana, null));

        assertThat(sixth.getResponse().getStatus()).isEqualTo(429);
        assertThat(JsonPath.<String>read(SseStreams.content(sixth), "$.code")).isEqualTo("rate.limited");
        assertThat(sixth.getResponse().getHeader("Retry-After")).isEqualTo("60");
        // Another person is not held back by Ana's streams.
        assertThat(SseStreams.open(mockMvc, ws, biaSession, TestUsers.signedIn(bia, null))
                        .getRequest()
                        .isAsyncStarted())
                .isTrue();
    }

    @Test
    void removedMemberStreamClosesAtOnce() throws Exception {
        MvcResult adult = SseStreams.open(mockMvc, ws, biaSession, TestUsers.signedIn(bia, null));
        MvcResult owner = SseStreams.open(mockMvc, ws, anaSession, TestUsers.signedIn(ana, null));

        assertThat(send(HttpMethod.DELETE, "/members/" + biaMembership, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(204);

        Eventually.within(WAIT, () -> assertThat(isComplete(adult)).isTrue());
        assertThat(isComplete(owner)).isFalse();
        Eventually.within(WAIT, () -> assertThat(SseStreams.changes(owner)).contains("{\"entity\":\"members\"}"));
        assertThat(SseStreams.changes(adult)).doesNotContain("{\"entity\":\"members\"}");
    }

    @Test
    void roleChangeClosesStream() throws Exception {
        MvcResult adult = SseStreams.open(mockMvc, ws, biaSession, TestUsers.signedIn(bia, null));

        assertThat(send(HttpMethod.PATCH, "/members/" + biaMembership, "{\"role\":\"VIEWER\"}")
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);

        Eventually.within(WAIT, () -> assertThat(isComplete(adult)).isTrue());
    }

    @Test
    void signedOutStreamClosesOnHeartbeat() throws Exception {
        MvcResult signedOut = SseStreams.open(mockMvc, ws, biaSession, TestUsers.signedIn(bia, null));
        MvcResult stays = SseStreams.open(mockMvc, ws, anaSession, TestUsers.signedIn(ana, null));

        heartbeat.beat();
        assertThat(isComplete(signedOut)).isFalse();

        signOut(biaSession);
        heartbeat.beat();

        Eventually.within(WAIT, () -> assertThat(isComplete(signedOut)).isTrue());
        assertThat(isComplete(stays)).isFalse();
        assertThat(SseStreams.content(stays)).contains(":\n\n");
    }

    @Test
    void closedStreamEndsCleanly() throws Exception {
        MvcResult adult = SseStreams.open(mockMvc, ws, biaSession, TestUsers.signedIn(bia, null));
        streams.closeMember(ws, biaMembership);
        Eventually.within(WAIT, () -> assertThat(isComplete(adult)).isTrue());

        MvcResult ended = mockMvc.perform(asyncDispatch(adult)).andReturn();

        assertThat(ended.getResolvedException()).isNull();
        assertThat(ended.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void signedOutStreamEndsCleanly() throws Exception {
        MvcResult adult = SseStreams.open(mockMvc, ws, biaSession, TestUsers.signedIn(bia, null));
        signOut(biaSession);
        heartbeat.beat();
        Eventually.within(WAIT, () -> assertThat(isComplete(adult)).isTrue());

        MvcResult ended = mockMvc.perform(asyncDispatch(adult)).andReturn();

        assertThat(ended.getResolvedException()).isNull();
        assertThat(ended.getResponse().getStatus()).isNotIn(401, 404).isEqualTo(200);
    }

    @Test
    void deletionRequestClosesFamilyStreams() throws Exception {
        MvcResult owner = SseStreams.open(mockMvc, ws, anaSession, TestUsers.signedIn(ana, null));
        MvcResult adult = SseStreams.open(mockMvc, ws, biaSession, TestUsers.signedIn(bia, null));
        MvcResult youth = SseStreams.open(mockMvc, ws, leoSession, TestUsers.signedIn(leo, null));

        assertThat(send(HttpMethod.POST, "/deletion", null).getResponse().getStatus())
                .isEqualTo(202);

        Eventually.within(WAIT, () -> {
            assertThat(isComplete(owner)).isTrue();
            assertThat(isComplete(adult)).isTrue();
            assertThat(isComplete(youth)).isTrue();
        });
    }

    // --- helpers ---

    private CsrfSession session(UUID user) {
        return CsrfSession.start(mvc, TestUsers.signedIn(user, null));
    }

    private void signOut(CsrfSession session) {
        assertThat(jdbc.sql("delete from spring_session where session_id = :id")
                        .param("id", session.sessionId())
                        .update())
                .isEqualTo(1);
    }

    /** A stream is complete once the emitter has finished and the container has asked for the async dispatch. */
    private static boolean isComplete(MvcResult stream) {
        return stream.getRequest().getAsyncContext() instanceof MockAsyncContext context
                && context.getDispatchedPath() != null;
    }

    /** A notice every stream receives, sent last, so that what is not there by then was not sent. */
    private void marker(MvcResult... receivers) throws Exception {
        assertThat(send(HttpMethod.POST, "/categories", "{\"name\":\"Marcador\",\"kind\":\"EXPENSE\"}")
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
        Eventually.within(WAIT, () -> {
            for (MvcResult receiver : receivers) {
                assertThat(SseStreams.changes(receiver)).anyMatch(change -> change.contains("\"category\""));
            }
        });
    }

    private UUID account(String name, UUID owner) {
        return jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, owner_membership_id)
                        values (:ws, :name, 'CHECKING', 'BRL', :owner) returning id""")
                .param("ws", ws)
                .param("name", name)
                .param("owner", owner)
                .query(UUID.class)
                .single();
    }

    /** Ana records an expense on the account, which publishes the notice. */
    private UUID record(UUID account, String amount, @Nullable String description) throws Exception {
        UUID id = TestIds.v7();
        MvcResult result = mockMvc.perform(post("/v1/workspaces/" + ws + "/transactions")
                        .with(TestUsers.signedIn(ana, null))
                        .with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)))
                        .header("Idempotency-Key", id.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","kind":"EXPENSE","amount":{"amount":"%s","currency":"BRL"},
                                 "occurredOn":"2026-11-20","accountId":"%s","categoryId":"%s","description":%s}""".formatted(
                                        id,
                                        amount,
                                        account,
                                        mercado,
                                        description == null ? "null" : "\"" + description + "\"")))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return id;
    }

    /** Ana writes to her family. */
    private MvcResult send(HttpMethod method, String path, @Nullable String body) throws Exception {
        var request = request(method, "/v1/workspaces/" + ws + path).with(TestUsers.signedIn(ana, null));
        if (method != HttpMethod.GET) {
            request.with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)));
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request).andReturn();
    }
}
