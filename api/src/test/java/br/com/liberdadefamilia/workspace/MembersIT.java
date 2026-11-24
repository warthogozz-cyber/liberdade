package br.com.liberdadefamilia.workspace;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.identity.UserDeletion;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import({TestcontainersConfiguration.class, MembersIT.Listener.class})
class MembersIT {

    private static final int TIMEOUT_SECONDS = 20;

    /** Records {@link MemberRoleChanged}, which the family publishes inside the changing transaction. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Listener {

        @Bean
        RoleChanges roleChanges() {
            return new RoleChanges();
        }
    }

    static class RoleChanges {

        private final List<MemberRoleChanged> seen = new CopyOnWriteArrayList<>();

        @EventListener
        void on(MemberRoleChanged event) {
            seen.add(event);
        }

        List<MemberRoleChanged> events() {
            return seen;
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
    Memberships memberships;

    @Autowired
    UserDeletion deletion;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RoleChanges roleChanges;

    @Autowired
    ApplicationEvents events;

    @Test
    void listsMembersForEveryRole() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID vera = users.create("Vera Teste", "vera@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
        workspaces.addMember(ws, vera, Role.VIEWER);

        for (UUID caller : List.of(ana, bia, vera)) {
            MvcTestResult result = get(caller, ws);

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(3);
            assertThat(result).bodyText().doesNotContain("email", "ana@example.com");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[?(@.you == true)].id")
                    .asArray()
                    .containsExactly(workspaces.membershipOf(ws, caller).toString());
        }
        assertThat(get(ana, ws))
                .bodyJson()
                .extractingPath("$[?(@.nickname == 'Bibi')].role")
                .asArray()
                .containsExactly("ADULT");
    }

    @Test
    void removedMemberStaysListed() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);

        assertThat(delete(ana, ws, biaMembership, 0)).hasStatus(204);

        MvcTestResult result = get(ana, ws);
        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$[?(@.id == '" + biaMembership + "')].status")
                .asArray()
                .containsExactly("REMOVED");
    }

    @Test
    void adultCannotManageMembers() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.ADULT);
        UUID anaMembership = workspaces.membershipOf(ws, ana);

        MvcTestResult patch = patch(bia, ws, anaMembership, "{\"nickname\":\"X\"}", 0);
        MvcTestResult remove = delete(bia, ws, anaMembership, 0);

        assertThat(patch).hasStatus(403);
        assertThat(patch).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
        assertThat(remove).hasStatus(403);
        assertThat(remove).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
    }

    @Test
    void viewerCanListButNotChange() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID vera = users.create("Vera Teste", "vera@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, vera, Role.VIEWER);

        assertThat(get(vera, ws)).hasStatusOk();
        MvcTestResult patch = patch(vera, ws, workspaces.membershipOf(ws, ana), "{\"nickname\":\"X\"}", 0);

        assertThat(patch).hasStatus(403);
        assertThat(patch).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
    }

    @Test
    void nicknameChangeNeedsNoRecentSignIn() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);

        MvcTestResult result = patch(ana, ws, biaMembership, "{\"nickname\":\"  Bibi  \"}", 30);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.nickname").isEqualTo("Bibi");
    }

    @Test
    void nicknameChangePublishes() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);
        events.clear();

        assertThat(patch(ana, ws, biaMembership, "{\"nickname\":\"Bibi\"}", 0)).hasStatusOk();
        assertThat(events.stream(MemberProfileChanged.class).toList())
                .containsExactly(new MemberProfileChanged(ws, biaMembership));

        // The same nickname again, and a request without one, change nothing and say nothing.
        assertThat(patch(ana, ws, biaMembership, "{\"nickname\":\"  Bibi \"}", 0))
                .hasStatusOk();
        assertThat(patch(ana, ws, biaMembership, "{\"role\":\"ADULT\"}", 0)).hasStatusOk();
        assertThat(events.stream(MemberProfileChanged.class).toList()).hasSize(1);
    }

    @Test
    void roleChangeNeedsRecentSignIn() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);

        MvcTestResult stale = patch(ana, ws, biaMembership, "{\"role\":\"VIEWER\"}", 30);
        assertThat(stale).hasStatus(403);
        assertThat(stale).bodyJson().extractingPath("$.code").isEqualTo("auth.reauthentication-required");
        assertThat(memberships.find(ws, biaMembership))
                .get()
                .extracting(MemberSummary::role)
                .isEqualTo(Role.ADULT);

        MvcTestResult fresh = patch(ana, ws, biaMembership, "{\"role\":\"VIEWER\"}", 0);
        assertThat(fresh).hasStatusOk();
        assertThat(fresh).bodyJson().extractingPath("$.role").isEqualTo("VIEWER");
    }

    @Test
    void sameRoleIsNotARoleChange() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);

        assertThat(patch(ana, ws, biaMembership, "{\"role\":\"ADULT\",\"nickname\":\"B\"}", 30))
                .hasStatusOk();
    }

    @Test
    void lastOwnerCannotStepDownOrLeave() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID anaMembership = workspaces.membershipOf(ws, ana);

        MvcTestResult demote = patch(ana, ws, anaMembership, "{\"role\":\"ADULT\"}", 0);
        MvcTestResult remove = delete(ana, ws, anaMembership, 0);

        assertThat(demote).hasStatus(422);
        assertThat(demote).bodyJson().extractingPath("$.code").isEqualTo("member.last-owner");
        assertThat(remove).hasStatus(422);
        assertThat(remove).bodyJson().extractingPath("$.code").isEqualTo("member.last-owner");
    }

    @Test
    void secondOwnerAllowsStepDown() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);

        MvcTestResult promote = patch(ana, ws, biaMembership, "{\"role\":\"OWNER\"}", 0);
        MvcTestResult stepDown = patch(ana, ws, workspaces.membershipOf(ws, ana), "{\"role\":\"ADULT\"}", 0);

        assertThat(promote).hasStatus(200);
        assertThat(stepDown).hasStatus(200);
        assertThat(activeOwners(ws)).isOne();
    }

    @Test
    void ownerCannotMakeThemselvesYouth() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.OWNER);

        MvcTestResult result =
                patch(ana, ws, workspaces.membershipOf(ws, ana), "{\"role\":\"YOUTH\",\"parentalConsent\":true}", 0);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("member.self-youth");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você não pode mudar o seu próprio papel para Jovem.");
        assertThat(memberships.find(ws, workspaces.membershipOf(ws, ana)))
                .get()
                .extracting(MemberSummary::role)
                .isEqualTo(Role.OWNER);
    }

    @Test
    void concurrentRemovalsKeepAnOwner() throws Exception {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID anaMembership = workspaces.membershipOf(ws, ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.OWNER);

        // A transaction of the test holds the owners' rows, so both requests are stopped inside the database before
        // either one can finish: the interleaving is forced, not hoped for.
        ExecutorService pool =
                Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
        List<Integer> statuses = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        try {
            List<Future<MvcTestResult>> calls = new ArrayList<>();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.sql("select id from memberships where workspace_id = :ws and role = 'OWNER' for update")
                        .param("ws", ws)
                        .query(UUID.class)
                        .list();
                calls.add(pool.submit(() -> delete(ana, ws, biaMembership, 0)));
                calls.add(pool.submit(() -> delete(bia, ws, anaMembership, 0)));
                awaitWaitingRequests(2);
            });
            for (Future<MvcTestResult> call : calls) {
                MvcTestResult result = call.get(TIMEOUT_SECONDS, SECONDS);
                statuses.add(result.getResponse().getStatus());
                if (result.getResponse().getStatus() == 422) {
                    codes.add(JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.code"));
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).containsExactlyInAnyOrder(204, 422);
        assertThat(codes).containsExactly("member.last-owner");
        assertThat(activeOwners(ws)).isOne();
    }

    /**
     * Ana asks for her account to be deleted while the nightly purge erases Bia, who owns two of Ana's families. The
     * request locks the Owners' rows in id order and the purge used to take Bia's rows in the order of the table, the
     * opposite one here: the request holds the lower row and wants the higher, the purge the other way round, and
     * Postgres aborts one of them. A transaction of the test holds the lower row so that both stop on it and the
     * interleaving is forced, not hoped for. Fifty rounds, each with its own people.
     */
    @Test
    void deletionRequestAndPurgeDoNotDeadlock() throws Exception {
        ExecutorService pool =
                Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
        try {
            for (int round = 0; round < 50; round++) {
                UUID ana = users.create("Ana Teste", "ana" + round + "@example.com");
                UUID bia = users.create("Bia Teste", "bia" + round + "@example.com");
                UUID caio = users.create("Caio Teste", "caio" + round + "@example.com");
                UUID lower = jdbc.sql("select uuidv7()").query(UUID.class).single();
                UUID higher = jdbc.sql("select uuidv7()").query(UUID.class).single();
                // The higher row goes in first, so a scan of the table meets it first.
                for (UUID row : List.of(higher, lower)) {
                    UUID ws = jdbc.sql("insert into workspaces (name) values ('Família Teste') returning id")
                            .query(UUID.class)
                            .single();
                    workspaces.addMember(ws, ana, Role.OWNER);
                    workspaces.addMember(ws, caio, Role.OWNER);
                    jdbc.sql(
                                    "insert into memberships (id, workspace_id, user_id, role) values (:id, :ws, :bia, 'OWNER')")
                            .param("id", row)
                            .param("ws", ws)
                            .param("bia", bia)
                            .update();
                }
                jdbc.sql("update users set deletion_requested_at = now() - interval '31 days' where id = :id")
                        .param("id", bia)
                        .update();

                List<Future<?>> calls = new ArrayList<>();
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    jdbc.sql("select id from memberships where id = :id for update")
                            .param("id", lower)
                            .query(UUID.class)
                            .list();
                    calls.add(pool.submit(() -> mvc.post()
                            .uri("/v1/me/deletion")
                            .with(session(ana, 0))
                            .with(CsrfSession.start(mvc, session(ana, 0)))
                            .exchange()));
                    awaitWaitingRequests(1);
                    calls.add(pool.submit(() -> {
                        new TransactionTemplate(transactionManager).executeWithoutResult(inner -> {
                            assertThat(deletion.lockIfDue(bia, Instant.now())).isTrue();
                            memberships.detachUser(bia);
                            deletion.purge(bia);
                        });
                        return null;
                    }));
                    awaitWaitingRequests(2);
                });

                MvcTestResult request = (MvcTestResult) calls.get(0).get(TIMEOUT_SECONDS, SECONDS);
                assertThat(request.getResponse().getStatus())
                        .as("request in round %d", round)
                        .isEqualTo(202);
                calls.get(1).get(TIMEOUT_SECONDS, SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void youthNeedsParentalConsent() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID teo = users.create("Teo Teste", "teo@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID teoMembership = workspaces.addMember(ws, teo, Role.ADULT);
        roleChanges.events().clear();

        MvcTestResult without = patch(ana, ws, teoMembership, "{\"role\":\"YOUTH\"}", 0);
        assertThat(without).hasStatus(422);
        assertThat(without).bodyJson().extractingPath("$.code").isEqualTo("member.parental-consent-required");
        assertThat(roleChanges.events()).isEmpty();

        MvcTestResult with = patch(ana, ws, teoMembership, "{\"role\":\"YOUTH\",\"parentalConsent\":true}", 0);
        assertThat(with).hasStatusOk();
        assertThat(with).bodyJson().extractingPath("$.role").isEqualTo("YOUTH");
        assertThat(jdbc.sql("select parental_consent_at is not null from memberships where id = :id")
                        .param("id", teoMembership)
                        .query(Boolean.class)
                        .single())
                .isTrue();
        assertThat(jdbc.sql("select parental_consent_by from memberships where id = :id")
                        .param("id", teoMembership)
                        .query(UUID.class)
                        .single())
                .isEqualTo(ana);
        assertThat(roleChanges.events())
                .containsExactly(new MemberRoleChanged(ws, teoMembership, Role.ADULT, Role.YOUTH, ana));
    }

    @Test
    void youthNeedsPasskeyOnlyAccount() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID teo = users.create("Teo Teste", "teo@example.com");
        jdbc.sql("""
                        insert into auth_identities (user_id, provider, subject, last_used_at)
                        values (:user, 'google', 'subject-teo-teste', now())""").param("user", teo).update();
        UUID ws = workspaces.createFor(ana);
        UUID teoMembership = workspaces.addMember(ws, teo, Role.ADULT);
        roleChanges.events().clear();

        MvcTestResult refused = patch(ana, ws, teoMembership, "{\"role\":\"YOUTH\",\"parentalConsent\":true}", 0);

        assertThat(refused).hasStatus(422);
        assertThat(refused).bodyJson().extractingPath("$.code").isEqualTo("member.youth-needs-passkey");
        assertThat(jdbc.sql("select role from memberships where id = :id")
                        .param("id", teoMembership)
                        .query(String.class)
                        .single())
                .isEqualTo("ADULT");
        assertThat(roleChanges.events()).isEmpty();
    }

    @Test
    void emptyPatchIs422() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);

        MvcTestResult result = patch(ana, ws, workspaces.membershipOf(ws, ana), "{}", 0);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
    }

    @Test
    void unknownMemberIs404() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);

        MvcTestResult result = patch(ana, ws, UUID.randomUUID(), "{\"nickname\":\"X\"}", 0);

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("member.not-found");
    }

    @Test
    void otherFamilyMemberIsNotFound() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID carla = users.create("Carla Teste", "carla@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID otherFamily = workspaces.createFor(carla);
        UUID strangerMembership = workspaces.membershipOf(otherFamily, carla);

        MvcTestResult patch = patch(ana, ws, strangerMembership, "{\"nickname\":\"X\"}", 0);
        MvcTestResult remove = delete(ana, ws, strangerMembership, 0);

        assertThat(patch).hasStatus(404);
        assertThat(patch).bodyJson().extractingPath("$.code").isEqualTo("member.not-found");
        assertThat(remove).hasStatus(404);
        assertThat(remove).bodyJson().extractingPath("$.code").isEqualTo("member.not-found");
        assertThat(memberships.isActive(otherFamily, strangerMembership)).isTrue();
    }

    @Test
    void removeEndsTheMembershipAndIsIdempotent() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);

        MvcTestResult stale = delete(ana, ws, biaMembership, 30);
        assertThat(stale).hasStatus(403);
        assertThat(stale).bodyJson().extractingPath("$.code").isEqualTo("auth.reauthentication-required");
        assertThat(delete(ana, ws, biaMembership, 0)).hasStatus(204);
        assertThat(delete(ana, ws, biaMembership, 0)).hasStatus(204);

        assertThat(memberships.isActive(ws, biaMembership)).isFalse();
        assertThat(memberships.find(ws, biaMembership))
                .get()
                .extracting(MemberSummary::status)
                .isEqualTo(MembershipStatus.REMOVED);
    }

    @Test
    void ownerRenamesFamily() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.ADULT);

        MvcTestResult denied = rename(bia, ws, "{\"name\":\"Outra\"}");
        MvcTestResult ok = rename(ana, ws, "{\"name\":\"  Os Silva  \"}");
        MvcTestResult blank = rename(ana, ws, "{\"name\":\"   \"}");

        assertThat(denied).hasStatus(403);
        assertThat(denied).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
        assertThat(ok).hasStatusOk();
        assertThat(ok).bodyJson().extractingPath("$.name").isEqualTo("Os Silva");
        assertThat(blank).hasStatus(422);
        assertThat(blank).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
        assertThat(mvc.get().uri("/v1/me").with(session(ana, 0)).exchange())
                .bodyJson()
                .extractingPath("$.workspaces[0].name")
                .isEqualTo("Os Silva");
    }

    @Test
    void detachUserEndsEveryMembership() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);

        memberships.detachUser(bia);

        assertThat(memberships.isActive(ws, biaMembership)).isFalse();
        assertThat(memberships.isSoleActiveOwnerOfLiveFamily(ana)).isTrue();
        assertThat(memberships.isSoleActiveOwnerOfLiveFamily(bia)).isFalse();
    }

    private long activeOwners(UUID ws) {
        return memberships.all(ws).stream()
                .filter(m -> m.role() == Role.OWNER && m.status() == MembershipStatus.ACTIVE)
                .count();
    }

    /**
     * Waits until that many requests are stopped on a database lock. The caller holds the lock in its own
     * transaction, and Postgres caches pg_stat_activity until that transaction ends, so each poll clears the
     * snapshot first.
     */
    private void awaitWaitingRequests(int expected) {
        long deadline = System.nanoTime() + SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            jdbc.sql("select pg_stat_clear_snapshot()").query(rs -> {});
            Long waiting = jdbc.sql("""
                            select count(*) from pg_stat_activity
                            where datname = current_database() and wait_event_type = 'Lock'""").query(Long.class).single();
            if (waiting >= expected) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("The requests never reached the lock");
    }

    private RequestPostProcessor session(UUID userId, int minutesAgo) {
        return minutesAgo == 0 ? TestUsers.signedIn(userId, null) : TestUsers.signedInMinutesAgo(userId, minutesAgo);
    }

    private MvcTestResult get(UUID userId, UUID ws) {
        return mvc.get()
                .uri("/v1/workspaces/" + ws + "/members")
                .with(session(userId, 0))
                .exchange();
    }

    private MvcTestResult patch(UUID userId, UUID ws, UUID membershipId, String body, int minutesAgo) {
        return mvc.patch()
                .uri("/v1/workspaces/" + ws + "/members/" + membershipId)
                .with(session(userId, minutesAgo))
                .with(CsrfSession.start(mvc, session(userId, minutesAgo)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult delete(UUID userId, UUID ws, UUID membershipId, int minutesAgo) {
        return mvc.delete()
                .uri("/v1/workspaces/" + ws + "/members/" + membershipId)
                .with(session(userId, minutesAgo))
                .with(CsrfSession.start(mvc, session(userId, minutesAgo)))
                .exchange();
    }

    private MvcTestResult rename(UUID userId, UUID ws, String body) {
        return mvc.patch()
                .uri("/v1/workspaces/" + ws)
                .with(session(userId, 0))
                .with(CsrfSession.start(mvc, session(userId, 0)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
