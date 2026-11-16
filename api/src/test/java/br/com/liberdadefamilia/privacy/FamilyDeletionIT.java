package br.com.liberdadefamilia.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.FamilyDeletion;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

/** An Owner asks for the family to be deleted: 30 days to change their mind, and nobody else sees it meanwhile. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FamilyDeletionIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    FamilyDeletion deletion;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Test
    void ownerSchedulesFamilyDeletion() {
        Family f = family();

        MvcTestResult result = request(f.ana, f.ws);

        assertThat(result).hasStatus(202);
        Instant scheduled = Instant.parse(JsonPath.read(body(result), "$.deletionScheduledFor"));
        assertThat(scheduled)
                .isBetween(
                        Instant.now().plus(Duration.ofDays(30)).minusSeconds(60),
                        Instant.now().plus(Duration.ofDays(30)));
        assertThat(get(f.bia, "/v1/workspaces/" + f.ws + "/accounts")).hasStatus(404);
        assertThat(get(f.bia, "/v1/workspaces/" + f.ws + "/accounts"))
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("workspace.not-found");
        assertThat(get(f.bia, "/v1/me"))
                .bodyJson()
                .extractingPath("$.workspaces")
                .asList()
                .isEmpty();
    }

    @Test
    void ownerSeesPendingFamily() {
        Family f = family();
        MvcTestResult scheduled = request(f.ana, f.ws);

        MvcTestResult me = get(f.ana, "/v1/me");

        assertThat(me).bodyJson().extractingPath("$.workspaces[0].id").isEqualTo(f.ws.toString());
        assertThat(me)
                .bodyJson()
                .extractingPath("$.workspaces[0].deletionScheduledFor")
                .isEqualTo(JsonPath.read(body(scheduled), "$.deletionScheduledFor"));
        assertThat(get(f.ana, "/v1/workspaces/" + f.ws + "/accounts")).hasStatus(404);
        assertThat(post(f.ana, "/v1/workspaces/" + f.ws + "/export", 0)).hasStatusOk();
    }

    @Test
    void repeatingTheRequestKeepsTheDateAndRecordsOnce() {
        Family f = family();

        MvcTestResult first = request(f.ana, f.ws);
        // The route hides a pending family from the guard (404), so asking again goes through the service.
        Instant again = deletion.schedule(f.ws, f.ana);

        assertThat(again).isEqualTo(Instant.parse(JsonPath.read(body(first), "$.deletionScheduledFor")));
        assertThat(actions(f.ws)).containsExactly("workspace.deletion_requested");
    }

    @Test
    void ownerCancels() {
        Family f = family();
        request(f.ana, f.ws);

        MvcTestResult cancelled = delete(f.ana, f.ws);

        assertThat(cancelled).hasStatus(204);
        assertThat(get(f.bia, "/v1/me"))
                .bodyJson()
                .extractingPath("$.workspaces[0].id")
                .isEqualTo(f.ws.toString());
        assertThat(get(f.bia, "/v1/workspaces/" + f.ws + "/accounts")).hasStatusOk();
        assertThat(get(f.ana, "/v1/me"))
                .bodyJson()
                .extractingPath("$.workspaces[0].deletionScheduledFor")
                .isNull();
        assertThat(actions(f.ws)).containsExactly("workspace.deletion_requested", "workspace.deletion_cancelled");
    }

    @Test
    void cancellingWithNothingPendingIsQuiet() {
        Family f = family();

        assertThat(delete(f.ana, f.ws)).hasStatus(204);

        assertThat(actions(f.ws)).isEmpty();
    }

    @Test
    void adultCannotDeleteFamily() {
        Family f = family();

        MvcTestResult result = request(f.bia, f.ws);

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
    }

    @Test
    void deletionNeedsRecentSignIn() {
        Family f = family();

        MvcTestResult result = post(f.ana, "/v1/workspaces/" + f.ws + "/deletion", 11);

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.reauthentication-required");
    }

    @Test
    void invitationToPendingFamilyIsRejected() {
        Family f = family();
        MvcTestResult created = mvc.post()
                .uri("/v1/workspaces/" + f.ws + "/invitations")
                .with(TestUsers.signedIn(f.ana, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(f.ana, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"VIEWER\",\"nickname\":\"Vovo\"}")
                .exchange();
        assertThat(created).hasStatus(201);
        String token = JsonPath.read(body(created), "$.token");
        request(f.ana, f.ws);

        MvcTestResult preview = mvc.post()
                .uri("/v1/invitations/preview")
                .with(CsrfSession.start(mvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}")
                .exchange();
        MvcTestResult accept = mvc.post()
                .uri("/v1/invitations/accept")
                .with(CsrfSession.start(mvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newUser\":{\"displayName\":\"Vovo Teste\"}}")
                .exchange();

        assertThat(preview).hasStatus(404);
        assertThat(preview).bodyJson().extractingPath("$.code").isEqualTo("invitation.not-found");
        assertThat(accept).hasStatus(404);
        assertThat(accept).bodyJson().extractingPath("$.code").isEqualTo("invitation.not-found");
    }

    private record Family(UUID ana, UUID bia, UUID ws) {}

    private Family family() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
        return new Family(ana, bia, ws);
    }

    private MvcTestResult request(UUID user, UUID ws) {
        return post(user, "/v1/workspaces/" + ws + "/deletion", 0);
    }

    private MvcTestResult post(UUID user, String uri, int minutesAgo) {
        RequestPostProcessor session = session(user, minutesAgo);
        return mvc.post()
                .uri(uri)
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();
    }

    private MvcTestResult delete(UUID user, UUID ws) {
        RequestPostProcessor session = session(user, 0);
        return mvc.delete()
                .uri("/v1/workspaces/" + ws + "/deletion")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();
    }

    private MvcTestResult get(UUID user, String uri) {
        return mvc.get().uri(uri).with(session(user, 0)).exchange();
    }

    private static RequestPostProcessor session(UUID user, int minutesAgo) {
        return minutesAgo == 0 ? TestUsers.signedIn(user, null) : TestUsers.signedInMinutesAgo(user, minutesAgo);
    }

    private List<String> actions(UUID ws) {
        return jdbc.sql("""
                        select action from audit_events
                        where workspace_id = :ws and action like 'workspace.deletion_%'
                        order by occurred_at, id""").param("ws", ws).query(String.class).list();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
