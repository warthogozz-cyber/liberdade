package br.com.liberdadefamilia.workspace;

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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * Recovery links. The clock stands still at {@link #NOW} so the 24 hours, the rate-limit window and the expiry are
 * exact; every public call comes from its own address.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecoveryIT.Config.class})
class RecoveryIT {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:15Z");
    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger();

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
     * transaction can already see that the link was used and the member recovered. Signing in before the commit would
     * fail all three.
     */
    static class SignInProbe {

        record Seen(UUID userId, boolean transactionActive, boolean recoveredVisible, boolean linkUsedVisible) {}

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
            Boolean recovered = separate.execute(status -> jdbc.sql(
                            "select exists (select 1 from memberships where user_id = :id and recovered_at is not null)")
                    .param("id", event.userId())
                    .query(Boolean.class)
                    .single());
            Boolean used = separate.execute(status -> jdbc.sql("""
                            select exists (select 1 from invitations i join memberships m on m.id = i.target_membership_id
                                           where m.user_id = :id and i.used_at is not null)""")
                    .param("id", event.userId())
                    .query(Boolean.class)
                    .single());
            seen.add(new Seen(event.userId(), active, Boolean.TRUE.equals(recovered), Boolean.TRUE.equals(used)));
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
        void on(RecoveryUsed event) {
            if (armed.get()) {
                throw new IllegalStateException("forced failure");
            }
        }
    }

    /** Records what recovery publishes, inside the transactions that make it happen. */
    static class Recorded {

        private final List<Object> seen = new CopyOnWriteArrayList<>();

        @EventListener
        void on(RecoveryCreated event) {
            seen.add(event);
        }

        @EventListener
        void on(RecoveryUsed event) {
            seen.add(event);
        }

        @EventListener
        void on(InvitationCreated event) {
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
    void ownerCreatesRecoveryForPasskeyOnlyMember() throws Exception {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        recorded.events().clear();

        MvcTestResult created = createRecovery(ana, ws, leoMembership);

        assertThat(created).hasStatus(201);
        String token = JsonPath.read(body(created), "$.token");
        assertThat(token).hasSize(43);
        assertThat(created).bodyJson().extractingPath("$.kind").isEqualTo("RECOVERY");
        assertThat(created).bodyJson().extractingPath("$.role").isEqualTo("YOUTH");
        assertThat(created).bodyJson().extractingPath("$.nickname").isNull();
        assertThat(created).bodyJson().extractingPath("$.createdByName").isEqualTo("Ana");
        assertThat(Instant.parse(JsonPath.read(body(created), "$.expiresAt")))
                .isEqualTo(NOW.plus(Duration.ofHours(24)));
        UUID id = idOf(created);
        // The database keeps the hash and nothing else of the token.
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
        assertThat(jdbc.sql("select target_membership_id from invitations where id = :id")
                        .param("id", id)
                        .query(UUID.class)
                        .single())
                .isEqualTo(leoMembership);
        assertThat(recorded.events()).containsExactly(new RecoveryCreated(ws, id, leoMembership, ana));
        MvcTestResult preview = preview(token);
        assertThat(preview).hasStatusOk();
        assertThat(preview).bodyJson().extractingPath("$.kind").isEqualTo("RECOVERY");
        assertThat(preview).bodyJson().extractingPath("$.nickname").isEqualTo("Leo");
        assertThat(preview).bodyJson().extractingPath("$.role").isEqualTo("YOUTH");
        assertThat(preview).bodyJson().extractingPath("$.workspaceName").isEqualTo("Família Teste");
    }

    @Test
    void adultCreatesRecoveryForYouth() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");

        MvcTestResult created = createRecovery(bia, ws, leoMembership);

        assertThat(created).hasStatus(201);
        assertThat(created).bodyJson().extractingPath("$.createdByName").isEqualTo("Bibi");
    }

    @Test
    void recoveryNeedsRecentSignIn() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");

        MvcTestResult stale = createRecovery(ana, ws, leoMembership, TestUsers.signedInMinutesAgo(ana, 11));

        assertThat(stale).hasStatus(403);
        assertThat(stale).bodyJson().extractingPath("$.code").isEqualTo("auth.reauthentication-required");
        assertThat(invitationCount(ws)).isZero();
        assertThat(createRecovery(ana, ws, leoMembership, TestUsers.signedInMinutesAgo(ana, 9)))
                .hasStatus(201);
    }

    @Test
    void adultCannotRecoverOwner() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.ADULT, "Bibi");

        MvcTestResult denied = createRecovery(bia, ws, workspaces.membershipOf(ws, ana));

        assertThat(denied).hasStatus(403);
        assertThat(denied).bodyJson().extractingPath("$.code").isEqualTo("invitation.role-forbidden");
        assertThat(invitationCount(ws)).isZero();
    }

    @Test
    void googleMemberUsesGoogle() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
        jdbc.sql("""
                        insert into auth_identities (user_id, provider, subject, last_used_at)
                        values (:user, 'google', 'subject-bia-teste', now())""").param("user", bia).update();

        MvcTestResult unavailable = createRecovery(ana, ws, biaMembership);

        assertThat(unavailable).hasStatus(422);
        assertThat(unavailable).bodyJson().extractingPath("$.code").isEqualTo("member.recovery-unavailable");
        assertThat(invitationCount(ws)).isZero();
    }

    @Test
    void removedMemberHasNoRecovery() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        jdbc.sql("update memberships set status = 'REMOVED' where id = :id")
                .param("id", leoMembership)
                .update();

        MvcTestResult removed = createRecovery(ana, ws, leoMembership);

        assertThat(removed).hasStatus(422);
        assertThat(removed).bodyJson().extractingPath("$.code").isEqualTo("member.removed");
        assertThat(invitationCount(ws)).isZero();
    }

    @Test
    void otherFamilyMemberIsNotFound() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID carla = users.create("Carla Teste", "carla@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID other = workspaces.createFor(carla);
        UUID strangerMembership = workspaces.addMember(other, leo, Role.YOUTH, "Leo");

        for (UUID missing : List.of(strangerMembership, UUID.randomUUID())) {
            MvcTestResult result = createRecovery(ana, ws, missing);
            assertThat(result).hasStatus(404);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("member.not-found");
        }
        assertThat(invitationCount(ws)).isZero();
        assertThat(invitationCount(other)).isZero();
    }

    @Test
    void viewerCannotCreateRecovery() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID vera = users.create("Vera Teste", "vera@example.com");
        UUID teo = users.create("Teo Teste", "teo@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, vera, Role.VIEWER);
        workspaces.addMember(ws, teo, Role.YOUTH);
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");

        for (UUID caller : List.of(vera, teo)) {
            MvcTestResult denied = createRecovery(caller, ws, leoMembership);
            assertThat(denied).hasStatus(403);
            assertThat(denied).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
        }
        assertThat(invitationCount(ws)).isZero();
    }

    @Test
    void recoveryIsRateLimited() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");

        // Invitations and recovery links draw on one budget of 20 an hour.
        for (int i = 0; i < 10; i++) {
            assertThat(createInvitation(ana, ws)).hasStatus(201);
        }
        for (int i = 0; i < 10; i++) {
            assertThat(createRecovery(ana, ws, leoMembership)).hasStatus(201);
        }
        MvcTestResult limited = createRecovery(ana, ws, leoMembership);

        assertThat(limited).hasStatus(429);
        assertThat(limited).bodyJson().extractingPath("$.code").isEqualTo("rate.limited");
        // The clock stands at 12:00:15 and the window is the hour.
        assertThat(limited).hasHeader(HttpHeaders.RETRY_AFTER, "3585");
        assertThat(createInvitation(ana, ws)).hasStatus(429);
        assertThat(invitationCount(ws)).isEqualTo(20);
    }

    @Test
    void recoverySignsInAsTarget() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        String token = tokenOf(createRecovery(ana, ws, leoMembership));
        CsrfSession anonymous = CsrfSession.start(mvc);
        long usersBefore = userCount();

        MvcTestResult accepted = post("/v1/invitations/accept", acceptBody(token, null), anonymous);

        assertThat(accepted).hasStatusOk();
        assertThat(accepted).bodyJson().extractingPath("$.mustRegisterPasskey").isEqualTo(true);
        assertThat(accepted).bodyJson().extractingPath("$.workspaceId").isEqualTo(ws.toString());
        assertThat(accepted).bodyJson().extractingPath("$.membershipId").isEqualTo(leoMembership.toString());
        Cookie session = accepted.getResponse().getCookie(CsrfSession.SESSION_COOKIE);
        assertThat(session).isNotNull();
        assertThat(CsrfSession.sessionIdOf(session)).isNotEqualTo(anonymous.sessionId());
        MvcTestResult me = mvc.get().uri("/v1/me").cookie(session).exchange();
        assertThat(me).hasStatusOk();
        assertThat(me).bodyJson().extractingPath("$.user.id").isEqualTo(leo.toString());
        assertThat(me).bodyJson().extractingPath("$.workspaces[0].id").isEqualTo(ws.toString());
        assertThat(Instant.parse(JsonPath.read(body(me), "$.workspaces[0].recoveredAt")))
                .isEqualTo(NOW);
        assertThat(recorded.events()).contains(new RecoveryUsed(ws, leoMembership, leo));
        // A member who was never recovered has no date.
        assertThat(me(ana))
                .bodyJson()
                .extractingPath("$.workspaces[0].recoveredAt")
                .isNull();
        assertThat(userCount()).isEqualTo(usersBefore);
    }

    @Test
    void recoveryReplacesOtherSession() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        String token = tokenOf(createRecovery(ana, ws, leoMembership));
        RequestPostProcessor signedInAsBia = TestUsers.signedIn(bia, null);
        CsrfSession biasSession = CsrfSession.start(mvc, signedInAsBia);

        MvcTestResult accepted = mvc.post()
                .uri("/v1/invitations/accept")
                .with(signedInAsBia)
                .with(biasSession)
                .with(from(nextAddress()))
                .contentType(JSON)
                .content(acceptBody(token, null))
                .exchange();

        assertThat(accepted).hasStatusOk();
        // A signed-in session's cookie is first refreshed under the old id, then rewritten under the new one when the
        // sign-in swaps it: a browser keeps the last, so that is the one that counts.
        Cookie session = lastSessionCookie(accepted);
        assertThat(session).isNotNull();
        assertThat(CsrfSession.sessionIdOf(session)).isNotEqualTo(biasSession.sessionId());
        MvcTestResult me = mvc.get().uri("/v1/me").cookie(session).exchange();
        assertThat(me).bodyJson().extractingPath("$.user.id").isEqualTo(leo.toString());
        // Bia herself is untouched by the swap.
        assertThat(memberships(ws, bia)).isOne();
        assertThat(jdbc.sql("select recovered_at is null from memberships where id = :id")
                        .param("id", workspaces.membershipOf(ws, bia))
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    @Test
    void recoveryIgnoresNewUser() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        String token = tokenOf(createRecovery(ana, ws, leoMembership));
        long usersBefore = userCount();

        MvcTestResult accepted =
                post("/v1/invitations/accept", acceptBody(token, "Intruso Teste"), CsrfSession.start(mvc));

        assertThat(accepted).hasStatusOk();
        assertThat(userCount()).isEqualTo(usersBefore);
        assertThat(jdbc.sql("select count(*) from users where display_name = 'Intruso Teste'")
                        .query(Long.class)
                        .single())
                .isZero();
        Cookie session = accepted.getResponse().getCookie(CsrfSession.SESSION_COOKIE);
        assertThat(session).isNotNull();
        assertThat(mvc.get().uri("/v1/me").cookie(session).exchange())
                .bodyJson()
                .extractingPath("$.user.id")
                .isEqualTo(leo.toString());
    }

    @Test
    void newLinkRevokesOld() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        String first = tokenOf(createRecovery(ana, ws, leoMembership));
        String second = tokenOf(createRecovery(ana, ws, leoMembership));

        MvcTestResult old = post("/v1/invitations/accept", acceptBody(first, null), CsrfSession.start(mvc));
        MvcTestResult oldPreview = preview(first);

        assertThat(old).hasStatus(410);
        assertThat(old).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
        assertThat(oldPreview).hasStatus(410);
        assertThat(oldPreview).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
        assertThat(jdbc.sql("select count(*) from invitations where workspace_id = :ws and revoked_at is not null")
                        .param("ws", ws)
                        .query(Long.class)
                        .single())
                .isOne();
        assertThat(post("/v1/invitations/accept", acceptBody(second, null), CsrfSession.start(mvc)))
                .hasStatusOk();
    }

    @Test
    void newLinkLeavesOtherMembersLinksAlone() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID mia = users.create("Mia Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        UUID miaMembership = workspaces.addMember(ws, mia, Role.YOUTH, "Mia");
        String forLeo = tokenOf(createRecovery(ana, ws, leoMembership));

        assertThat(createRecovery(ana, ws, miaMembership)).hasStatus(201);

        assertThat(preview(forLeo)).hasStatusOk();
    }

    @Test
    void recoveryExpiresIn24h() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        String token = tokenOf(createRecovery(ana, ws, leoMembership));
        // The last second it still works, and looking at it does not use it up.
        clock.freeze(NOW.plus(Duration.ofHours(24)).minusSeconds(1));
        assertThat(preview(token)).hasStatusOk();

        clock.freeze(NOW.plus(Duration.ofHours(24)).plusSeconds(1));

        MvcTestResult accepted = post("/v1/invitations/accept", acceptBody(token, null), CsrfSession.start(mvc));
        assertThat(accepted).hasStatus(410);
        assertThat(accepted).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
        assertThat(preview(token)).hasStatus(410);
        assertThat(recoveredCount(ws)).isZero();
    }

    @Test
    void recoveryLinkUsedOnce() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        String token = tokenOf(createRecovery(ana, ws, leoMembership));
        assertThat(post("/v1/invitations/accept", acceptBody(token, null), CsrfSession.start(mvc)))
                .hasStatusOk();

        MvcTestResult again = post("/v1/invitations/accept", acceptBody(token, null), CsrfSession.start(mvc));

        assertThat(again).hasStatus(410);
        assertThat(again).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
        assertThat(again.getResponse().getCookie(CsrfSession.SESSION_COOKIE)).isNull();
    }

    @Test
    void recoveryTargetRemovedIsGone() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        String token = tokenOf(createRecovery(ana, ws, leoMembership));
        jdbc.sql("update memberships set status = 'REMOVED' where id = :id")
                .param("id", leoMembership)
                .update();
        long signedInBefore = signedInSessions();

        MvcTestResult accepted = post("/v1/invitations/accept", acceptBody(token, null), CsrfSession.start(mvc));

        assertThat(accepted).hasStatus(410);
        assertThat(accepted).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
        assertThat(recoveredCount(ws)).isZero();
        assertThat(jdbc.sql("select used_at is null from invitations where workspace_id = :ws")
                        .param("ws", ws)
                        .query(Boolean.class)
                        .single())
                .isTrue();
        assertThat(signedInSessions()).isEqualTo(signedInBefore);
        assertThat(recorded.events()).noneMatch(RecoveryUsed.class::isInstance);
        MvcTestResult preview = preview(token);
        assertThat(preview).hasStatus(410);
        assertThat(preview).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
    }

    @Test
    void recoveryTargetPromotedIsGone() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
        UUID leoMembership = workspaces.addMember(ws, leo, Role.ADULT, "Leo");
        String token = tokenOf(createRecovery(bia, ws, leoMembership));
        // The owner promotes Leo after Bia made the link: Bia must not end up signed in as an owner.
        jdbc.sql("update memberships set role = 'OWNER' where id = :id")
                .param("id", leoMembership)
                .update();
        long signedInBefore = signedInSessions();

        MvcTestResult preview = preview(token);
        assertThat(preview).hasStatus(410);
        assertThat(preview).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
        MvcTestResult accepted = post("/v1/invitations/accept", acceptBody(token, null), CsrfSession.start(mvc));

        assertThat(accepted).hasStatus(410);
        assertThat(accepted).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
        assertNothingWritten(ws, signedInBefore);
    }

    @Test
    void recoveryFromRemovedCreatorIsGone() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        String token = tokenOf(createRecovery(bia, ws, leoMembership));
        jdbc.sql("update memberships set status = 'REMOVED' where id = :id")
                .param("id", biaMembership)
                .update();
        long signedInBefore = signedInSessions();

        MvcTestResult accepted = post("/v1/invitations/accept", acceptBody(token, null), CsrfSession.start(mvc));

        assertThat(accepted).hasStatus(410);
        assertThat(accepted).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
        assertNothingWritten(ws, signedInBefore);
    }

    @Test
    void recoveryFromDemotedCreatorIsGone() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        String token = tokenOf(createRecovery(bia, ws, leoMembership));
        jdbc.sql("update memberships set role = 'VIEWER' where id = :id")
                .param("id", biaMembership)
                .update();
        long signedInBefore = signedInSessions();

        MvcTestResult accepted = post("/v1/invitations/accept", acceptBody(token, null), CsrfSession.start(mvc));

        assertThat(accepted).hasStatus(410);
        assertThat(accepted).bodyJson().extractingPath("$.code").isEqualTo("invitation.gone");
        assertNothingWritten(ws, signedInBefore);
    }

    private void assertNothingWritten(UUID ws, long signedInBefore) {
        assertThat(recoveredCount(ws)).isZero();
        assertThat(jdbc.sql("select used_at is null from invitations where workspace_id = :ws")
                        .param("ws", ws)
                        .query(Boolean.class)
                        .single())
                .isTrue();
        assertThat(signedInSessions()).isEqualTo(signedInBefore);
        assertThat(recorded.events()).noneMatch(RecoveryUsed.class::isInstance);
    }

    @Test
    void recoverySignInHappensAfterTheCommit() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        String token = tokenOf(createRecovery(ana, ws, leoMembership));
        signInProbe.seen().clear();
        RequestPostProcessor signedInAsBia = TestUsers.signedIn(bia, null);

        // Signed in as someone else: the recovery still signs in the target, and only once the accept has committed.
        MvcTestResult accepted = mvc.post()
                .uri("/v1/invitations/accept")
                .with(signedInAsBia)
                .with(CsrfSession.start(mvc, signedInAsBia))
                .with(from(nextAddress()))
                .contentType(JSON)
                .content(acceptBody(token, null))
                .exchange();

        assertThat(accepted).hasStatusOk();
        assertThat(signInProbe.seen()).containsExactly(new SignInProbe.Seen(leo, false, true, true));
    }

    @Test
    void failedRecoveryUndoesEverythingAndLeavesNoSession() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        String token = tokenOf(createRecovery(ana, ws, leoMembership));
        long signedInBefore = signedInSessions();
        CsrfSession anonymous = CsrfSession.start(mvc);

        failing.arm();
        MvcTestResult failed = post("/v1/invitations/accept", acceptBody(token, null), anonymous);
        failing.disarm();

        assertThat(failed.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        Cookie held = failed.getResponse().getCookie(CsrfSession.SESSION_COOKIE);
        assertThat(mvc.get()
                        .uri("/v1/me")
                        .cookie(held != null ? held : anonymous.cookie())
                        .exchange())
                .hasStatus(401);
        assertThat(signedInSessions()).isEqualTo(signedInBefore);
        assertThat(recoveredCount(ws)).isZero();
        assertThat(jdbc.sql("select used_at is null from invitations where workspace_id = :ws")
                        .param("ws", ws)
                        .query(Boolean.class)
                        .single())
                .isTrue();
        // The link was not spent: it works once the failure is gone.
        assertThat(post("/v1/invitations/accept", acceptBody(token, null), CsrfSession.start(mvc)))
                .hasStatusOk();
    }

    @Test
    void recoveryLinksAreNotListedAndDoNotMakeTheFamilyInvited() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        String token = tokenOf(createRecovery(ana, ws, leoMembership));
        MvcTestResult invite = createInvitation(ana, ws);
        assertThat(invite).hasStatus(201);

        MvcTestResult list = mvc.get()
                .uri("/v1/workspaces/" + ws + "/invitations")
                .with(TestUsers.signedIn(ana, null))
                .exchange();

        assertThat(list).hasStatusOk();
        assertThat(list)
                .bodyJson()
                .extractingPath("$[*].id")
                .asArray()
                .containsExactly(idOf(invite).toString());
        assertThat(list).bodyJson().extractingPath("$[*].kind").asArray().containsExactly("INVITE");
        assertThat(list).bodyText().doesNotContain(token);

        // A family of one whose only pending link is a recovery link (inserted straight, for a person who left)
        // is not "invited".
        UUID carla = users.create("Carla Teste", "carla@example.com");
        UUID leaver = users.create("Saiu Teste", null);
        UUID other = workspaces.createFor(carla);
        UUID leaverMembership = workspaces.addMember(other, leaver, Role.YOUTH, "Saiu");
        jdbc.sql("update memberships set status = 'REMOVED' where id = :id")
                .param("id", leaverMembership)
                .update();
        jdbc.sql("""
                        insert into invitations (workspace_id, kind, role, target_membership_id, token_hash,
                                                 created_by, expires_at)
                        values (:ws, 'RECOVERY', 'YOUTH', :target, :hash, :by, :expires)""")
                .param("ws", other)
                .param("target", leaverMembership)
                .param("hash", sha256("x".repeat(43)))
                .param("by", carla)
                .param(
                        "expires",
                        java.time.OffsetDateTime.ofInstant(NOW.plus(Duration.ofHours(1)), java.time.ZoneOffset.UTC))
                .update();
        assertThat(me(carla))
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

    private long recoveredCount(UUID ws) {
        return jdbc.sql("select count(*) from memberships where workspace_id = :ws and recovered_at is not null")
                .param("ws", ws)
                .query(Long.class)
                .single();
    }

    /** Sessions that carry a sign-in; a rolled-back or refused accept must not leave one behind. */
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

    private static @Nullable Cookie lastSessionCookie(MvcTestResult result) {
        Cookie last = null;
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (CsrfSession.SESSION_COOKIE.equals(cookie.getName())) {
                last = cookie;
            }
        }
        return last;
    }

    private static String nextAddress() {
        int n = NEXT_ADDRESS.incrementAndGet();
        return "10.3." + (n / 250) + "." + (n % 250 + 1);
    }

    private static byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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

    private MvcTestResult createInvitation(UUID userId, UUID ws) {
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/invitations")
                .with(TestUsers.signedIn(userId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(userId, null)))
                .contentType(JSON)
                .content("{\"role\":\"VIEWER\",\"nickname\":\"Vovo\"}")
                .exchange();
    }

    private MvcTestResult createRecovery(UUID userId, UUID ws, UUID membershipId) {
        return createRecovery(userId, ws, membershipId, TestUsers.signedIn(userId, null));
    }

    private MvcTestResult createRecovery(UUID userId, UUID ws, UUID membershipId, RequestPostProcessor signedIn) {
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/members/" + membershipId + "/recovery")
                .with(signedIn)
                .with(CsrfSession.start(mvc, signedIn))
                .exchange();
    }

    private MvcTestResult preview(String token) {
        return post("/v1/invitations/preview", "{\"token\":\"" + token + "\"}", CsrfSession.start(mvc));
    }

    private MvcTestResult post(String uri, String body, CsrfSession csrf) {
        return mvc.post()
                .uri(uri)
                .with(csrf)
                .with(from(nextAddress()))
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
