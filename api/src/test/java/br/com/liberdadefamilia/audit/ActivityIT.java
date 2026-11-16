package br.com.liberdadefamilia.audit;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The history a person reads of their own: what they did and what was done to them, and nothing about anyone else. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ActivityIT {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger();

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    AuditTrail trail;

    @Test
    void needsSignIn() {
        assertThat(mvc.get().uri("/v1/me/activity").exchange()).hasStatus(401);
    }

    @Test
    void showsOwnAndTargeted() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
        nickname(ws, ana, "Aninha");

        assertThat(patchRole(ana, ws, biaMembership, "VIEWER")).hasStatusOk();

        MvcTestResult forAna = activity(ana);
        assertThat(forAna).hasStatusOk();
        assertThat(forAna).bodyJson().extractingPath("$").asArray().hasSize(1);
        assertThat(forAna).bodyJson().extractingPath("$[0].action").isEqualTo("member.role_changed");
        assertThat(forAna).bodyJson().extractingPath("$[0].byMe").isEqualTo(true);
        assertThat(forAna).bodyJson().extractingPath("$[0].actorName").isEqualTo("Aninha");
        assertThat(forAna).bodyJson().extractingPath("$[0].workspaceId").isEqualTo(ws.toString());
        assertThat(forAna)
                .bodyJson()
                .extractingPath("$[0].metadata")
                .isEqualTo(Map.of("from", "ADULT", "to", "VIEWER"));
        MvcTestResult forBia = activity(bia);
        assertThat(forBia).bodyJson().extractingPath("$").asArray().hasSize(1);
        assertThat(forBia).bodyJson().extractingPath("$[0].action").isEqualTo("member.role_changed");
        assertThat(forBia).bodyJson().extractingPath("$[0].byMe").isEqualTo(false);
        assertThat(forBia).bodyJson().extractingPath("$[0].actorName").isEqualTo("Aninha");
        // Nobody sees an address or an email.
        assertThat(forBia).bodyText().doesNotContain("ana@example.com");
    }

    @Test
    void actorNameFallsBackToTheDisplayName() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);
        nickname(ws, ana, null);

        assertThat(patchRole(ana, ws, biaMembership, "VIEWER")).hasStatusOk();

        assertThat(activity(bia)).bodyJson().extractingPath("$[0].actorName").isEqualTo("Ana Teste");
    }

    @Test
    void actorNameIsNullOnceTheAccountIsGone() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);
        UUID gone = users.create("Carla Teste", "carla@example.com");
        trail.record(new AuditEntry(
                ws, gone, "member.role_changed", "MEMBERSHIP", biaMembership, Map.of("from", "ADULT", "to", "VIEWER")));

        jdbc.sql("delete from users where id = :id").param("id", gone).update();

        MvcTestResult forBia = activity(bia);
        assertThat(forBia).bodyJson().extractingPath("$[0].actorName").isNull();
        assertThat(forBia).bodyJson().extractingPath("$[0].byMe").isEqualTo(false);
    }

    @Test
    void hidesOthers() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID vera = users.create("Vera Teste", "vera@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);
        workspaces.addMember(ws, vera, Role.VIEWER);

        assertThat(patchRole(ana, ws, biaMembership, "OWNER")).hasStatusOk();
        // Events whose only link to Vera is the shared family: a rename, a module switch and an invitation.
        assertThat(send(mvc.patch(), ana, "/v1/workspaces/" + ws, "{\"name\":\"Os Teste\"}"))
                .hasStatusOk();
        assertThat(send(mvc.put(), bia, "/v1/workspaces/" + ws + "/modules/planning", "{\"enabled\":true}"))
                .hasStatusOk();
        assertThat(send(
                        mvc.post(),
                        ana,
                        "/v1/workspaces/" + ws + "/invitations",
                        "{\"role\":\"VIEWER\",\"nickname\":\"Vovo\"}"))
                .hasStatus(201);

        assertThat(activity(vera)).hasStatusOk();
        assertThat(activity(vera)).bodyJson().extractingPath("$").asArray().isEmpty();
        // Bia sees the change to her own role and the module she switched, and nothing else.
        assertThat(activity(bia))
                .bodyJson()
                .extractingPath("$[*].action")
                .asArray()
                .containsExactly("module.toggled", "member.role_changed");
    }

    @Test
    void recoveryShowsForTarget() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", null);
        UUID ws = workspaces.createFor(ana);
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        nickname(ws, ana, "Aninha");
        MvcTestResult recovery = mvc.post()
                .uri("/v1/workspaces/" + ws + "/members/" + leoMembership + "/recovery")
                .with(TestUsers.signedIn(ana, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)))
                .exchange();
        assertThat(recovery).hasStatus(201);
        String token = JsonPath.read(body(recovery), "$.token");
        String address = "10.8.0." + NEXT_ADDRESS.incrementAndGet();
        assertThat(mvc.post()
                        .uri("/v1/invitations/accept")
                        .with(CsrfSession.start(mvc))
                        .with(request -> {
                            request.setRemoteAddr(address);
                            return request;
                        })
                        .contentType(JSON)
                        .content("{\"token\":\"" + token + "\"}")
                        .exchange())
                .hasStatusOk();

        MvcTestResult forLeo = activity(leo);

        // Newest first: the sign-in the link brings, the use of the link, then its creation.
        assertThat(forLeo)
                .bodyJson()
                .extractingPath("$[*].action")
                .asArray()
                .containsExactly("auth.signed_in", "recovery.used", "recovery.created");
        assertThat(forLeo).bodyJson().extractingPath("$[1].byMe").isEqualTo(true);
        assertThat(forLeo).bodyJson().extractingPath("$[2].byMe").isEqualTo(false);
        assertThat(forLeo).bodyJson().extractingPath("$[2].actorName").isEqualTo("Aninha");
    }

    @Test
    void removedMembershipStillShowsItsHistory() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);
        jdbc.sql("update memberships set status = 'REMOVED' where id = :id")
                .param("id", biaMembership)
                .update();
        record(ws, ana, "member.removed", "MEMBERSHIP", biaMembership, Instant.now());

        assertThat(activity(bia)).bodyJson().extractingPath("$[0].action").isEqualTo("member.removed");
    }

    @Test
    void limitedTo100() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        Instant base = Instant.now().minus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        for (int i = 0; i < 120; i++) {
            record(null, ana, "auth.signed_in", "USER", ana, base.plusSeconds(i * 60L));
        }

        MvcTestResult result = activity(ana);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(100);
        List<String> times = JsonPath.read(body(result), "$[*].occurredAt");
        assertThat(Instant.parse(times.getFirst())).isEqualTo(base.plusSeconds(119 * 60L));
        assertThat(Instant.parse(times.getLast())).isEqualTo(base.plusSeconds(20 * 60L));
        assertThat(times)
                .isSortedAccordingTo(
                        Comparator.<String, Instant>comparing(Instant::parse).reversed());
        assertThat(trail.activityOf(ana, 5)).hasSize(5);
        assertThat(trail.activityOf(ana, 1000)).hasSize(100);
    }

    @Test
    void allActivityIsNotLimited() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        Instant base = Instant.now().minus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        for (int i = 0; i < 120; i++) {
            record(null, ana, "auth.signed_in", "USER", ana, base.plusSeconds(i * 60L));
        }

        assertThat(trail.allActivityOf(ana)).hasSize(120);
        assertThat(trail.allActivityOf(ana).getFirst().occurredAt()).isEqualTo(base.plusSeconds(119 * 60L));
    }

    @Test
    void onlyTheLast12Months() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        Instant now = Instant.now();
        record(
                null,
                ana,
                "auth.signed_in",
                "USER",
                ana,
                OffsetDateTime.now(ZoneOffset.UTC).minusMonths(12).minusDays(1).toInstant());
        record(
                null,
                ana,
                "auth.signed_in",
                "USER",
                ana,
                OffsetDateTime.now(ZoneOffset.UTC).minusMonths(11).toInstant());
        record(null, ana, "auth.signed_in", "USER", ana, now);

        assertThat(activity(ana)).bodyJson().extractingPath("$").asArray().hasSize(2);
    }

    @Test
    void sameInstantKeepsANewestFirstOrder() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        Instant at = Instant.now().minus(1, ChronoUnit.DAYS);
        UUID first = record(null, ana, "auth.signed_in", "USER", ana, at);
        UUID second = record(null, ana, "auth.signed_in", "USER", ana, at);

        assertThat(activity(ana))
                .bodyJson()
                .extractingPath("$[*].id")
                .asArray()
                .containsExactly(second.toString(), first.toString());
    }

    private void nickname(UUID ws, UUID userId, @Nullable String nickname) {
        jdbc.sql("update memberships set nickname = :nickname where workspace_id = :ws and user_id = :user")
                .param("nickname", nickname)
                .param("ws", ws)
                .param("user", userId)
                .update();
    }

    private UUID record(
            @Nullable UUID ws, UUID actor, String action, String targetType, UUID targetId, Instant occurredAt) {
        return jdbc.sql("""
                        insert into audit_events (workspace_id, actor_user_id, action, target_type, target_id, occurred_at)
                        values (:ws, :actor, :action, :targetType, :targetId, :occurredAt)
                        returning id""")
                .param("ws", ws)
                .param("actor", actor)
                .param("action", action)
                .param("targetType", targetType)
                .param("targetId", targetId)
                .param("occurredAt", OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                .query(UUID.class)
                .single();
    }

    private MvcTestResult activity(UUID userId) {
        return mvc.get().uri("/v1/me/activity").with(session(userId)).exchange();
    }

    private static RequestPostProcessor session(UUID userId) {
        return TestUsers.signedIn(userId, null);
    }

    private MvcTestResult send(
            org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder builder,
            UUID caller,
            String uri,
            String json) {
        return builder.uri(uri)
                .with(session(caller))
                .with(CsrfSession.start(mvc, session(caller)))
                .contentType(JSON)
                .content(json)
                .exchange();
    }

    private MvcTestResult patchRole(UUID caller, UUID ws, UUID membershipId, String role) {
        return mvc.patch()
                .uri("/v1/workspaces/" + ws + "/members/" + membershipId)
                .with(session(caller))
                .with(CsrfSession.start(mvc, session(caller)))
                .contentType(JSON)
                .content("{\"role\":\"" + role + "\"}")
                .exchange();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
