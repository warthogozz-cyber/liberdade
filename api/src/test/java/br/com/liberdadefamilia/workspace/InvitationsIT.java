package br.com.liberdadefamilia.workspace;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.identity.SignedIn;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
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
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Single-use invitations. The clock stands still at {@link #NOW} so the 7 days, the rate-limit windows and the expiry
 * are exact; every public call comes from its own address, so the limit of 10 a minute only matters where a test
 * aims at it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, InvitationsIT.Config.class})
class InvitationsIT {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:15Z");
    private static final int TIMEOUT_SECONDS = 20;
    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger();
    private static final String VIEWER_BODY = "{\"role\":\"VIEWER\",\"nickname\":\"Vovo\"}";
    private static final String ADULT_BODY = "{\"role\":\"ADULT\",\"nickname\":\"Bibi\"}";

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

        @Bean
        Failing failing() {
            return new Failing();
        }

        @Bean
        SignInProbe signInProbe(PlatformTransactionManager transactionManager, JdbcClient jdbc) {
            return new SignInProbe(transactionManager, jdbc);
        }
    }

    /**
     * Looks at the moment {@link SignedIn} fires: whether a transaction is still open, and whether a separate
     * transaction can already see the person. Signing in before the commit would fail both.
     */
    static class SignInProbe {

        record Seen(boolean transactionActive, boolean personVisible) {}

        private final PlatformTransactionManager transactionManager;
        private final JdbcClient jdbc;
        private final List<Seen> seen = new CopyOnWriteArrayList<>();

        SignInProbe(PlatformTransactionManager transactionManager, JdbcClient jdbc) {
            this.transactionManager = transactionManager;
            this.jdbc = jdbc;
        }

        @EventListener
        void on(SignedIn event) {
            boolean active = TransactionSynchronizationManager.isActualTransactionActive();
            TransactionTemplate separate = new TransactionTemplate(transactionManager);
            separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Boolean visible = separate.execute(status -> jdbc.sql("select exists (select 1 from users where id = :id)")
                    .param("id", event.userId())
                    .query(Boolean.class)
                    .single());
            seen.add(new Seen(active, Boolean.TRUE.equals(visible)));
        }

        List<Seen> seen() {
            return seen;
        }
    }

    /** Throws from inside the accepting transaction, when armed, to prove that everything done there is undone. */
    static class Failing {

        private final AtomicBoolean armed = new AtomicBoolean();

        void arm() {
            armed.set(true);
        }

        void disarm() {
            armed.set(false);
        }

        @EventListener
        void on(MemberJoined event) {
            if (armed.get()) {
                throw new IllegalStateException("forced failure");
            }
        }
    }

    /** Records what the invitations publish, inside the transactions that make it happen. */
    static class Recorded {

        private final List<Object> seen = new CopyOnWriteArrayList<>();

        @EventListener
        void on(InvitationCreated event) {
            seen.add(event);
        }

        @EventListener
        void on(InvitationRevoked event) {
            seen.add(event);
        }

        @EventListener
        void on(MemberJoined event) {
            seen.add(event);
        }

        List<Object> events() {
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
    TestClock clock;

    @Autowired
    Recorded recorded;

    @Autowired
    Failing failing;

    @Autowired
    SignInProbe signInProbe;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void stopClock() {
        clock.freeze(NOW);
        recorded.events().clear();
        signInProbe.seen().clear();
    }

    @AfterEach
    void releaseClock() {
        failing.disarm();
        clock.release();
    }

    @Test
    void ownerInvitesAdult() throws Exception {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);

        MvcTestResult created = createInvitation(ana, ws, ADULT_BODY);

        assertThat(created).hasStatus(201);
        String token = JsonPath.read(body(created), "$.token");
        assertThat(token).hasSize(43);
        assertThat(created).bodyJson().extractingPath("$.role").isEqualTo("ADULT");
        assertThat(created).bodyJson().extractingPath("$.nickname").isEqualTo("Bibi");
        assertThat(created).bodyJson().extractingPath("$.kind").isEqualTo("INVITE");
        assertThat(Instant.parse(JsonPath.read(body(created), "$.expiresAt"))).isEqualTo(NOW.plus(Duration.ofDays(7)));
        UUID id = idOf(created);
        // The database keeps the hash and nothing else of the token: no column, rendered as text, contains it.
        assertThat(jdbc.sql("select i::text from invitations i where id = :id")
                        .param("id", id)
                        .query(String.class)
                        .single())
                .doesNotContain(token);
        assertThat(jdbc.sql("select token_hash from invitations where id = :id")
                        .param("id", id)
                        .query(byte[].class)
                        .single())
                .isEqualTo(sha256(token));
        assertThat(recorded.events())
                .containsExactly(new InvitationCreated(ws, id, InvitationKind.INVITE, Role.ADULT, ana));
    }

    @Test
    void adultCannotInviteOwner() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.ADULT, "Bibi");

        MvcTestResult denied = createInvitation(bia, ws, "{\"role\":\"OWNER\",\"nickname\":\"Chefe\"}");

        assertThat(denied).hasStatus(403);
        assertThat(denied).bodyJson().extractingPath("$.code").isEqualTo("invitation.role-forbidden");
        assertThat(createInvitation(bia, ws, VIEWER_BODY)).hasStatus(201);
    }

    @Test
    void youthInviteNeedsConsent() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);

        MvcTestResult without = createInvitation(ana, ws, "{\"role\":\"YOUTH\",\"nickname\":\"Leo\"}");
        MvcTestResult declined =
                createInvitation(ana, ws, "{\"role\":\"YOUTH\",\"nickname\":\"Leo\",\"parentalConsent\":false}");

        for (MvcTestResult result : List.of(without, declined)) {
            assertThat(result).hasStatus(422);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("invitation.parental-consent-required");
        }
        assertThat(invitationCount(ws)).isZero();
        assertThat(createInvitation(ana, ws, "{\"role\":\"YOUTH\",\"nickname\":\"Leo\",\"parentalConsent\":true}"))
                .hasStatus(201);
    }

    @Test
    void invalidRequestIs422() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);

        for (String body : List.of(
                "{\"role\":\"ADULT\",\"nickname\":\"   \"}",
                "{\"role\":\"ADULT\"}",
                "{\"nickname\":\"Bibi\"}",
                "{\"role\":\"ADULT\",\"nickname\":\"" + "x".repeat(41) + "\"}")) {
            MvcTestResult result = createInvitation(ana, ws, body);
            assertThat(result).hasStatus(422);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
        }
        MvcTestResult shortToken = accept("curto", null, null);
        assertThat(shortToken).hasStatus(422);
        assertThat(shortToken).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
    }

    @Test
    void viewerAndYouthCannotInvite() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID vera = users.create("Vera Teste", "vera@example.com");
        UUID teo = users.create("Teo Teste", "teo@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, vera, Role.VIEWER);
        workspaces.addMember(ws, teo, Role.YOUTH);

        for (UUID caller : List.of(vera, teo)) {
            MvcTestResult denied = createInvitation(caller, ws, VIEWER_BODY);
            assertThat(denied).hasStatus(403);
            assertThat(denied).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
            MvcTestResult list = mvc.get()
                    .uri("/v1/workspaces/" + ws + "/invitations")
                    .with(TestUsers.signedIn(caller, null))
                    .exchange();
            assertThat(list).hasStatus(403);
            assertThat(list).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
        }
        assertThat(invitationCount(ws)).isZero();
    }

    @Test
    void listHidesToken() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
        String token = tokenOf(createInvitation(bia, ws, VIEWER_BODY));

        MvcTestResult list = list(ana, ws);

        assertThat(list).hasStatusOk();
        assertThat(list).bodyJson().extractingPath("$").asArray().hasSize(1);
        assertThat(list).bodyJson().extractingPath("$[0].createdByName").isEqualTo("Bibi");
        assertThat(list).bodyJson().extractingPath("$[0].nickname").isEqualTo("Vovo");
        assertThat(list).bodyJson().extractingPath("$[0].token").isNull();
        assertThat(list).bodyText().doesNotContain(token);
    }

    @Test
    void listLeavesOutUsedRevokedAndExpired() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        String used = tokenOf(createInvitation(ana, ws, "{\"role\":\"ADULT\",\"nickname\":\"Um\"}"));
        MvcTestResult revoked = createInvitation(ana, ws, "{\"role\":\"ADULT\",\"nickname\":\"Dois\"}");
        MvcTestResult pending = createInvitation(ana, ws, "{\"role\":\"ADULT\",\"nickname\":\"Tres\"}");
        assertThat(accept(used, null, bia)).hasStatusOk();
        assertThat(revoke(ana, ws, idOf(revoked))).hasStatus(204);

        assertThat(list(ana, ws))
                .bodyJson()
                .extractingPath("$[*].id")
                .asArray()
                .containsExactly(idOf(pending).toString());

        clock.freeze(NOW.plus(Duration.ofDays(7)).plusSeconds(1));
        assertThat(list(ana, ws)).bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void previewShowsFamily() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        String token = tokenOf(createInvitation(ana, ws, VIEWER_BODY));

        MvcTestResult preview = preview(token, nextAddress());

        assertThat(preview).hasStatusOk();
        assertThat(preview).bodyJson().extractingPath("$.workspaceName").isEqualTo("Família Teste");
        assertThat(preview).bodyJson().extractingPath("$.invitedBy").isEqualTo("Ana");
        assertThat(preview).bodyJson().extractingPath("$.role").isEqualTo("VIEWER");
        assertThat(preview).bodyJson().extractingPath("$.nickname").isEqualTo("Vovo");
        assertThat(preview).bodyJson().extractingPath("$.kind").isEqualTo("INVITE");
        assertThat(jdbc.sql("select used_at is null from invitations where workspace_id = :ws")
                        .param("ws", ws)
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    @Test
    void signedInAccepts() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID carla = users.create("Carla Teste", "carla@example.com");
        UUID ws = workspaces.createFor(ana);
        String token = tokenOf(createInvitation(ana, ws, ADULT_BODY));

        MvcTestResult accepted = accept(token, null, bia);

        assertThat(accepted).hasStatusOk();
        assertThat(accepted).bodyJson().extractingPath("$.workspaceId").isEqualTo(ws.toString());
        assertThat(accepted)
                .bodyJson()
                .extractingPath("$.membershipId")
                .isEqualTo(workspaces.membershipOf(ws, bia).toString());
        assertThat(accepted).bodyJson().extractingPath("$.mustRegisterPasskey").isEqualTo(false);
        MvcTestResult members = mvc.get()
                .uri("/v1/workspaces/" + ws + "/members")
                .with(TestUsers.signedIn(ana, null))
                .exchange();
        assertThat(members)
                .bodyJson()
                .extractingPath("$[?(@.nickname == 'Bibi')].role")
                .asArray()
                .containsExactly("ADULT");
        assertThat(recorded.events())
                .filteredOn(MemberJoined.class::isInstance)
                .containsExactly(new MemberJoined(ws, workspaces.membershipOf(ws, bia), bia, Role.ADULT, "Bibi", null));

        MvcTestResult again = accept(token, null, carla);
        assertThat(again).hasStatus(410);
        assertThat(again).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
        assertThat(memberships(ws, carla)).isZero();
    }

    @Test
    void sameCallerRetryGetsSameResult() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        String token = tokenOf(createInvitation(ana, ws, ADULT_BODY));

        MvcTestResult first = accept(token, null, bia);
        MvcTestResult retry = accept(token, null, bia);

        assertThat(first).hasStatusOk();
        assertThat(retry).hasStatusOk();
        assertThat(retry).bodyJson().extractingPath("$.workspaceId").isEqualTo(ws.toString());
        assertThat(retry)
                .bodyJson()
                .extractingPath("$.membershipId")
                .isEqualTo(workspaces.membershipOf(ws, bia).toString());
        assertThat(memberships(ws, bia)).isOne();
        assertThat(recorded.events()).filteredOn(MemberJoined.class::isInstance).hasSize(1);
    }

    @Test
    void otherCallerAfterUseGets410() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID carla = users.create("Carla Teste", "carla@example.com");
        UUID ws = workspaces.createFor(ana);
        String token = tokenOf(createInvitation(ana, ws, ADULT_BODY));
        assertThat(accept(token, null, bia)).hasStatusOk();

        MvcTestResult other = accept(token, null, carla);

        assertThat(other).hasStatus(410);
        assertThat(other).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
        assertThat(memberships(ws, carla)).isZero();
    }

    @Test
    void removedMemberRetryGets410() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        String token = tokenOf(createInvitation(ana, ws, ADULT_BODY));
        assertThat(accept(token, null, bia)).hasStatusOk();
        jdbc.sql("update memberships set status = 'REMOVED' where id = :id")
                .param("id", workspaces.membershipOf(ws, bia))
                .update();

        MvcTestResult retry = accept(token, null, bia);

        assertThat(retry).hasStatus(410);
        assertThat(retry).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
    }

    @Test
    void memberCannotAcceptAgain() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID carla = users.create("Carla Teste", "carla@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
        String token = tokenOf(createInvitation(ana, ws, VIEWER_BODY));

        MvcTestResult duplicate = accept(token, null, bia);

        assertThat(duplicate).hasStatus(409);
        assertThat(duplicate).bodyJson().extractingPath("$.code").isEqualTo("invitation.already-member");
        assertThat(jdbc.sql("select used_at is null from invitations where workspace_id = :ws")
                        .param("ws", ws)
                        .query(Boolean.class)
                        .single())
                .isTrue();
        assertThat(accept(token, null, carla)).hasStatusOk();
    }

    @Test
    void removedMemberIsReactivated() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
        jdbc.sql("update memberships set status = 'REMOVED' where id = :id")
                .param("id", biaMembership)
                .update();
        String token = tokenOf(createInvitation(ana, ws, VIEWER_BODY));

        MvcTestResult accepted = accept(token, null, bia);

        assertThat(accepted).hasStatusOk();
        assertThat(accepted).bodyJson().extractingPath("$.membershipId").isEqualTo(biaMembership.toString());
        MvcTestResult members = mvc.get()
                .uri("/v1/workspaces/" + ws + "/members")
                .with(TestUsers.signedIn(ana, null))
                .exchange();
        assertThat(members).bodyJson().extractingPath("$").asArray().hasSize(2);
        String own = "$[?(@.id == '" + biaMembership + "')]";
        assertThat(members).bodyJson().extractingPath(own + ".status").asArray().containsExactly("ACTIVE");
        assertThat(members).bodyJson().extractingPath(own + ".role").asArray().containsExactly("VIEWER");
        assertThat(members)
                .bodyJson()
                .extractingPath(own + ".nickname")
                .asArray()
                .containsExactly("Vovo");
    }

    @Test
    void anonymousNeedsNewUser() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        String token = tokenOf(createInvitation(ana, ws, VIEWER_BODY));

        MvcTestResult result = accept(token, null, null);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("invitation.new-user-required");
        assertThat(jdbc.sql("select used_at is null from invitations where workspace_id = :ws")
                        .param("ws", ws)
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    @Test
    void anonymousCreatesPasskeyOnlyUser() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        String token = tokenOf(createInvitation(ana, ws, VIEWER_BODY));
        CsrfSession anonymous = CsrfSession.start(mvc);

        MvcTestResult accepted =
                post("/v1/invitations/accept", acceptBody(token, "Vovo Teste"), nextAddress(), anonymous);

        assertThat(accepted).hasStatusOk();
        assertThat(accepted).bodyJson().extractingPath("$.mustRegisterPasskey").isEqualTo(true);
        Cookie session = accepted.getResponse().getCookie(CsrfSession.SESSION_COOKIE);
        assertThat(session).isNotNull();
        assertThat(CsrfSession.sessionIdOf(session)).isNotEqualTo(anonymous.sessionId());
        MvcTestResult me = mvc.get().uri("/v1/me").cookie(session).exchange();
        assertThat(me).hasStatusOk();
        assertThat(me).bodyJson().extractingPath("$.workspaces[0].id").isEqualTo(ws.toString());
        assertThat(me).bodyJson().extractingPath("$.user.displayName").isEqualTo("Vovo Teste");
        Map<String, Object> person = jdbc.sql("""
                        select u.email, (select count(*) from auth_identities a where a.user_id = u.id) as identities
                        from users u join memberships m on m.user_id = u.id
                        where m.workspace_id = :ws and m.nickname = 'Vovo'""").param("ws", ws).query().singleRow();
        assertThat(person).containsEntry("email", null).containsEntry("identities", 0L);
    }

    @Test
    void expiredIsGone() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        String token = tokenOf(createInvitation(ana, ws, ADULT_BODY));
        // The last second it still works, and looking at it does not use it up.
        clock.freeze(NOW.plus(Duration.ofDays(7)).minusSeconds(1));
        assertThat(preview(token, nextAddress())).hasStatusOk();

        clock.freeze(NOW.plus(Duration.ofDays(7)).plusSeconds(1));

        MvcTestResult preview = preview(token, nextAddress());
        assertThat(preview).hasStatus(410);
        assertThat(preview).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
        MvcTestResult accepted = accept(token, null, bia);
        assertThat(accepted).hasStatus(410);
        assertThat(accepted).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
        assertThat(memberships(ws, bia)).isZero();
    }

    @Test
    void inviteFromRemovedCreatorIsGone() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID carla = users.create("Carla Teste", "carla@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
        String token = tokenOf(createInvitation(bia, ws, ADULT_BODY));
        jdbc.sql("update memberships set status = 'REMOVED' where id = :id")
                .param("id", biaMembership)
                .update();

        MvcTestResult accepted = accept(token, null, carla);

        assertThat(accepted).hasStatus(410);
        assertThat(accepted).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
        assertThat(memberships(ws, carla)).isZero();
        assertThat(jdbc.sql("select used_at is null from invitations where workspace_id = :ws")
                        .param("ws", ws)
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    @Test
    void revokedIsGone() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        MvcTestResult created = createInvitation(ana, ws, ADULT_BODY);
        String token = tokenOf(created);

        assertThat(revoke(ana, ws, idOf(created))).hasStatus(204);

        MvcTestResult accepted = accept(token, null, bia);
        assertThat(accepted).hasStatus(410);
        assertThat(accepted).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
        MvcTestResult preview = preview(token, nextAddress());
        assertThat(preview).hasStatus(410);
        assertThat(preview).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
        assertThat(memberships(ws, bia)).isZero();
    }

    @Test
    void revokeIsIdempotentAndScoped() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID carla = users.create("Carla Teste", "carla@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID other = workspaces.createFor(carla);
        MvcTestResult created = createInvitation(ana, ws, ADULT_BODY);
        recorded.events().clear();

        assertThat(revoke(ana, ws, idOf(created))).hasStatus(204);
        assertThat(revoke(ana, ws, idOf(created))).hasStatus(204);

        assertThat(recorded.events()).containsExactly(new InvitationRevoked(ws, idOf(created), ana));
        for (MvcTestResult missing : List.of(
                revoke(ana, ws, UUID.randomUUID()),
                // Another family's invitation answers as if it did not exist.
                revoke(carla, other, idOf(created)))) {
            assertThat(missing).hasStatus(404);
            assertThat(missing).bodyJson().extractingPath("$.code").isEqualTo("invitation.not-found");
        }
    }

    @Test
    void unknownTokenIsNotFound() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String invented = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        MvcTestResult preview = preview(invented, nextAddress());
        MvcTestResult accepted = accept(invented, "Alguem Teste", null);

        for (MvcTestResult result : List.of(preview, accepted)) {
            assertThat(result).hasStatus(404);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("invitation.not-found");
        }
        assertThat(jdbc.sql("select count(*) from users where display_name = 'Alguem Teste'")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void concurrentAcceptsUseOnce() throws Exception {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID carla = users.create("Carla Teste", "carla@example.com");
        UUID ws = workspaces.createFor(ana);
        MvcTestResult created = createInvitation(ana, ws, ADULT_BODY);
        String token = tokenOf(created);

        // A transaction of the test holds the invitation's row, so both requests are stopped on the database lock
        // before either one can finish: the interleaving is forced, not hoped for. Without the row lock in the accept
        // query the requests would still wait, at the update that marks the link used, and then both would succeed.
        ExecutorService pool =
                Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
        List<Integer> statuses = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        try {
            List<Future<MvcTestResult>> calls = new ArrayList<>();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.sql("select id from invitations where id = :id for update")
                        .param("id", idOf(created))
                        .query(UUID.class)
                        .list();
                calls.add(pool.submit(() -> accept(token, null, bia)));
                calls.add(pool.submit(() -> accept(token, null, carla)));
                awaitWaitingRequests(2);
            });
            for (Future<MvcTestResult> call : calls) {
                MvcTestResult result = call.get(TIMEOUT_SECONDS, SECONDS);
                statuses.add(result.getResponse().getStatus());
                if (result.getResponse().getStatus() == 410) {
                    codes.add(JsonPath.read(body(result), "$.code"));
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 410);
        assertThat(codes).containsExactly("invitation.gone");
        assertThat(memberships(ws, bia) + memberships(ws, carla)).isOne();
    }

    @Test
    void youthGetsConsentCopied() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", "leo@example.com");
        UUID ws = workspaces.createFor(ana);
        String token = tokenOf(
                createInvitation(ana, ws, "{\"role\":\"YOUTH\",\"nickname\":\"Leo\",\"parentalConsent\":true}"));

        assertThat(accept(token, null, leo)).hasStatusOk();

        Map<String, Object> membership =
                jdbc.sql("""
                        select role, nickname, parental_consent_at is not null as consented, parental_consent_by
                        from memberships where workspace_id = :ws and user_id = :user""").param("ws", ws).param("user", leo).query().singleRow();
        assertThat(membership)
                .containsEntry("role", "YOUTH")
                .containsEntry("nickname", "Leo")
                .containsEntry("consented", true)
                .containsEntry("parental_consent_by", ana);
        assertThat(recorded.events())
                .filteredOn(MemberJoined.class::isInstance)
                .containsExactly(new MemberJoined(ws, workspaces.membershipOf(ws, leo), leo, Role.YOUTH, "Leo", ana));
    }

    @Test
    void youthInviteNeedsPasskeyOnlyAccount() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", "leo@example.com");
        jdbc.sql("""
                        insert into auth_identities (user_id, provider, subject, last_used_at)
                        values (:user, 'google', 'subject-leo-teste', now())""").param("user", leo).update();
        UUID ws = workspaces.createFor(ana);
        String token = tokenOf(
                createInvitation(ana, ws, "{\"role\":\"YOUTH\",\"nickname\":\"Leo\",\"parentalConsent\":true}"));

        MvcTestResult refused = accept(token, null, leo);

        assertThat(refused).hasStatus(422);
        assertThat(refused).bodyJson().extractingPath("$.code").isEqualTo("invitation.youth-needs-passkey");
        assertThat(memberships(ws, leo)).isZero();
        // The link is still there for a new passkey-only account.
        assertThat(accept(token, "Leo", null)).hasStatusOk();
    }

    @Test
    void failedAcceptUndoesTheNewUserAndLeavesNoSession() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        MvcTestResult created = createInvitation(ana, ws, VIEWER_BODY);
        String token = tokenOf(created);
        long usersBefore = userCount();
        long signedInBefore = signedInSessions();
        CsrfSession anonymous = CsrfSession.start(mvc);

        failing.arm();
        MvcTestResult failed = mvc.post()
                .uri("/v1/invitations/accept")
                .with(anonymous)
                .with(from(nextAddress()))
                .contentType(JSON)
                .content(acceptBody(token, "Fulano Falho"))
                .exchange();
        failing.disarm();

        assertThat(failed.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        // Whichever cookie the client now holds, it must not be a signed-in session.
        Cookie held = failed.getResponse().getCookie(CsrfSession.SESSION_COOKIE);
        assertThat(mvc.get()
                        .uri("/v1/me")
                        .cookie(held != null ? held : anonymous.cookie())
                        .exchange())
                .hasStatus(401);
        assertThat(signedInSessions()).isEqualTo(signedInBefore);
        assertThat(userCount()).isEqualTo(usersBefore);
        assertThat(jdbc.sql("select used_at is null from invitations where id = :id")
                        .param("id", idOf(created))
                        .query(Boolean.class)
                        .single())
                .isTrue();
        assertThat(acceptFrom(token, "Fulano Falho", nextAddress())).hasStatusOk();
    }

    @Test
    void signInHappensAfterTheCommit() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        String token = tokenOf(createInvitation(ana, ws, VIEWER_BODY));
        signInProbe.seen().clear();

        assertThat(acceptFrom(token, "Pessoa Confirmada", nextAddress())).hasStatusOk();

        assertThat(signInProbe.seen()).containsExactly(new SignInProbe.Seen(false, true));
    }

    @Test
    void writesNeedTheCsrfToken() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        String token = tokenOf(createInvitation(ana, ws, VIEWER_BODY));
        long usersBefore = userCount();

        for (String uri : List.of("/v1/invitations/accept", "/v1/invitations/preview")) {
            MvcTestResult denied = mvc.post()
                    .uri(uri)
                    .with(from(nextAddress()))
                    .contentType(JSON)
                    .content(acceptBody(token, "Fulano Falho"))
                    .exchange();
            assertThat(denied).hasStatus(403);
            assertThat(denied).bodyJson().extractingPath("$.code").isEqualTo("csrf.invalid");
        }
        assertThat(memberships(ws, ana)).isOne();
        assertThat(userCount()).isEqualTo(usersBefore);
    }

    @Test
    void previewIsRateLimited() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        String token = tokenOf(createInvitation(ana, ws, VIEWER_BODY));
        String address = nextAddress();

        for (int i = 0; i < 10; i++) {
            assertThat(preview(token, address)).hasStatusOk();
        }
        MvcTestResult limited = preview(token, address);

        assertThat(limited).hasStatus(429);
        assertThat(limited).bodyJson().extractingPath("$.code").isEqualTo("rate.limited");
        // The clock stands at 12:00:15, so the minute ends in 45 seconds.
        assertThat(limited).hasHeader(HttpHeaders.RETRY_AFTER, "45");
        // One budget for both public routes, and per address.
        MvcTestResult acceptLimited = acceptFrom(token, "Alguem Teste", address);
        assertThat(acceptLimited).hasStatus(429);
        assertThat(acceptLimited).bodyJson().extractingPath("$.code").isEqualTo("rate.limited");
        assertThat(preview(token, nextAddress())).hasStatusOk();
    }

    @Test
    void createIsRateLimited() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);

        for (int i = 0; i < 20; i++) {
            assertThat(createInvitation(ana, ws, VIEWER_BODY)).hasStatus(201);
        }
        MvcTestResult limited = createInvitation(ana, ws, VIEWER_BODY);

        assertThat(limited).hasStatus(429);
        assertThat(limited).bodyJson().extractingPath("$.code").isEqualTo("rate.limited");
        // The clock stands at 12:00:15 and the window is the hour.
        assertThat(limited).hasHeader(HttpHeaders.RETRY_AFTER, "3585");
        assertThat(invitationCount(ws)).isEqualTo(20);
    }

    @Test
    void familyInvitedFlag() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        assertThat(me(ana))
                .bodyJson()
                .extractingPath("$.workspaces[0].familyInvited")
                .isEqualTo(false);

        MvcTestResult created = createInvitation(ana, ws, VIEWER_BODY);

        assertThat(me(ana))
                .bodyJson()
                .extractingPath("$.workspaces[0].familyInvited")
                .isEqualTo(true);
        // A revoked invitation no longer counts.
        assertThat(revoke(ana, ws, idOf(created))).hasStatus(204);
        assertThat(me(ana))
                .bodyJson()
                .extractingPath("$.workspaces[0].familyInvited")
                .isEqualTo(false);
    }

    private long invitationCount(UUID ws) {
        return jdbc.sql("select count(*) from invitations where workspace_id = :ws")
                .param("ws", ws)
                .query(Long.class)
                .single();
    }

    /** Sessions that carry a sign-in; a rolled-back accept must not leave one behind. */
    private long signedInSessions() {
        return jdbc.sql("select count(*) from spring_session_attributes where attribute_name = :name")
                .param("name", "liberdade.authenticatedAt")
                .query(Long.class)
                .single();
    }

    private long userCount() {
        return jdbc.sql("select count(*) from users").query(Long.class).single();
    }

    private long memberships(UUID ws, UUID userId) {
        return jdbc.sql("select count(*) from memberships where workspace_id = :ws and user_id = :user")
                .param("ws", ws)
                .param("user", userId)
                .query(Long.class)
                .single();
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

    private static String nextAddress() {
        int n = NEXT_ADDRESS.incrementAndGet();
        return "10.2." + (n / 250) + "." + (n % 250 + 1);
    }

    private static byte[] sha256(String token) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String tokenOf(MvcTestResult created) {
        assertThat(created).hasStatus(201);
        return JsonPath.read(body(created), "$.token");
    }

    private static UUID idOf(MvcTestResult created) {
        return UUID.fromString(JsonPath.read(body(created), "$.id"));
    }

    private static String acceptBody(String token, @Nullable String displayName) {
        return displayName == null
                ? "{\"token\":\"" + token + "\"}"
                : "{\"token\":\"" + token + "\",\"newUser\":{\"displayName\":\"" + displayName + "\"}}";
    }

    private MvcTestResult me(UUID userId) {
        return mvc.get().uri("/v1/me").with(TestUsers.signedIn(userId, null)).exchange();
    }

    private MvcTestResult list(UUID userId, UUID ws) {
        return mvc.get()
                .uri("/v1/workspaces/" + ws + "/invitations")
                .with(TestUsers.signedIn(userId, null))
                .exchange();
    }

    private MvcTestResult createInvitation(UUID userId, UUID ws, String body) {
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/invitations")
                .with(TestUsers.signedIn(userId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(userId, null)))
                .contentType(JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult revoke(UUID userId, UUID ws, UUID invitationId) {
        return mvc.delete()
                .uri("/v1/workspaces/" + ws + "/invitations/" + invitationId)
                .with(TestUsers.signedIn(userId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(userId, null)))
                .exchange();
    }

    private MvcTestResult preview(String token, String address) {
        return post("/v1/invitations/preview", "{\"token\":\"" + token + "\"}", address, CsrfSession.start(mvc));
    }

    /** Accepts anonymously, as a new person with this name. */
    private MvcTestResult acceptFrom(String token, String displayName, String address) {
        return post("/v1/invitations/accept", acceptBody(token, displayName), address, CsrfSession.start(mvc));
    }

    /** Accepts anonymously without saying who is new. */
    private MvcTestResult accept(String token, @Nullable String displayName, @Nullable UUID signedInAs) {
        String body = acceptBody(token, displayName);
        if (signedInAs == null) {
            return post("/v1/invitations/accept", body, nextAddress(), CsrfSession.start(mvc));
        }
        RequestPostProcessor signedIn = TestUsers.signedIn(signedInAs, null);
        return mvc.post()
                .uri("/v1/invitations/accept")
                .with(signedIn)
                .with(CsrfSession.start(mvc, signedIn))
                .with(from(nextAddress()))
                .contentType(JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult post(String uri, String body, String address, CsrfSession csrf) {
        return mvc.post()
                .uri(uri)
                .with(csrf)
                .with(from(address))
                .contentType(JSON)
                .content(body)
                .exchange();
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
