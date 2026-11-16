package br.com.liberdadefamilia.workspace;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.Map;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, WorkspaceGuardIT.ProbeController.class})
class WorkspaceGuardIT {

    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Test
    void ownerReads() {
        UUID ownerId = users.create("Ana Teste", "ana@example.com");
        UUID workspaceId = workspaces.createFor(ownerId);

        var result = get(ownerId, "/v1/workspaces/" + workspaceId + "/probe/read");

        assertThat(result).hasStatusOk().hasBodyTextEqualTo("OWNER");
    }

    @Test
    void viewerReads() {
        UUID workspaceId = workspaces.createFor(users.create("Ana Teste", "ana@example.com"));
        UUID viewerId = users.create("Bia Teste", "bia@example.com");
        workspaces.addMember(workspaceId, viewerId, VIEWER);

        var result = get(viewerId, "/v1/workspaces/" + workspaceId + "/probe/read");

        assertThat(result).hasStatusOk().hasBodyTextEqualTo("VIEWER");
    }

    @Test
    void viewerCannotWrite() {
        UUID workspaceId = workspaces.createFor(users.create("Ana Teste", "ana@example.com"));
        UUID viewerId = users.create("Bia Teste", "bia@example.com");
        workspaces.addMember(workspaceId, viewerId, VIEWER);

        var result = post(viewerId, "/v1/workspaces/" + workspaceId + "/probe/write");

        assertForbidden(result);
    }

    @Test
    void adultWrites() {
        UUID workspaceId = workspaces.createFor(users.create("Ana Teste", "ana@example.com"));
        UUID adultId = users.create("Bia Teste", "bia@example.com");
        workspaces.addMember(workspaceId, adultId, ADULT);

        var result = post(adultId, "/v1/workspaces/" + workspaceId + "/probe/write");

        assertThat(result).hasStatusOk().hasBodyTextEqualTo("ADULT");
    }

    @Test
    void youthIsDeniedOnlyWhereNotAllowed() {
        UUID workspaceId = workspaces.createFor(users.create("Ana Teste", "ana@example.com"));
        UUID youthId = users.create("Caio Teste", "caio@example.com");
        workspaces.addMember(workspaceId, youthId, YOUTH);

        var allowed = get(youthId, "/v1/workspaces/" + workspaceId + "/probe/youth");
        var notListed = post(youthId, "/v1/workspaces/" + workspaceId + "/probe/write");

        assertThat(allowed).hasStatusOk().hasBodyTextEqualTo("YOUTH");
        assertForbidden(notListed);
    }

    @Test
    void userOfAnotherFamilyGets404() {
        UUID workspaceId = workspaces.createFor(users.create("Ana Teste", "ana@example.com"));
        UUID otherId = users.create("Bia Teste", "bia@example.com");
        workspaces.createFor(otherId);

        var result = get(otherId, "/v1/workspaces/" + workspaceId + "/probe/read");

        assertNotFound(result);
    }

    @Test
    void unknownFamilyGets404() {
        UUID ownerId = users.create("Ana Teste", "ana@example.com");
        workspaces.createFor(ownerId);

        var result = get(ownerId, "/v1/workspaces/" + UUID.randomUUID() + "/probe/read");

        assertNotFound(result);
    }

    /** Membership is checked before role: a VIEWER elsewhere gets 404 on a write route, never a telling 403. */
    @Test
    void userOfAnotherFamilyGets404OnWrite() {
        UUID workspaceId = workspaces.createFor(users.create("Ana Teste", "ana@example.com"));
        UUID otherFamilyId = workspaces.createFor(users.create("Caio Teste", "caio@example.com"));
        UUID viewerId = users.create("Bia Teste", "bia@example.com");
        workspaces.addMember(otherFamilyId, viewerId, VIEWER);

        var result = post(viewerId, "/v1/workspaces/" + workspaceId + "/probe/write");

        assertNotFound(result);
    }

    @Test
    void notAUuidGets404() {
        UUID ownerId = users.create("Ana Teste", "ana@example.com");
        workspaces.createFor(ownerId);

        var result = get(ownerId, "/v1/workspaces/abc/probe/read");

        assertNotFound(result);
    }

    @Test
    void removedMembershipGets404() {
        UUID workspaceId = workspaces.createFor(users.create("Ana Teste", "ana@example.com"));
        UUID viewerId = users.create("Bia Teste", "bia@example.com");
        UUID membershipId = workspaces.addMember(workspaceId, viewerId, VIEWER);
        jdbc.sql("update memberships set status = 'REMOVED' where id = :id")
                .param("id", membershipId)
                .update();

        var result = get(viewerId, "/v1/workspaces/" + workspaceId + "/probe/read");

        assertNotFound(result);
    }

    @Test
    void familyBeingDeletedGets404() {
        UUID ownerId = users.create("Ana Teste", "ana@example.com");
        UUID workspaceId = workspaces.createFor(ownerId);
        jdbc.sql("update workspaces set deletion_requested_at = now() where id = :id")
                .param("id", workspaceId)
                .update();

        var result = get(ownerId, "/v1/workspaces/" + workspaceId + "/probe/read");

        assertNotFound(result);
    }

    @Test
    void unannotatedRouteIsDenied() {
        UUID ownerId = users.create("Ana Teste", "ana@example.com");
        UUID workspaceId = workspaces.createFor(ownerId);

        var result = get(ownerId, "/v1/workspaces/" + workspaceId + "/probe/open");

        assertForbidden(result);
    }

    @Test
    void guardHandsTheWholeMember() {
        UUID workspaceId = workspaces.createFor(users.create("Ana Teste", "ana@example.com"));
        jdbc.sql("update workspaces set timezone = 'America/Manaus' where id = :id")
                .param("id", workspaceId)
                .update();
        UUID viewerId = users.create("Bia Teste", "bia@example.com");
        UUID membershipId = workspaces.addMember(workspaceId, viewerId, VIEWER);
        Map<String, Object> row =
                jdbc.sql("""
                        select m.workspace_id, m.id, m.user_id, m.role, w.timezone, w.base_currency
                        from memberships m join workspaces w on w.id = m.workspace_id
                        where m.id = :id""").param("id", membershipId).query().singleRow();

        var result = get(viewerId, "/v1/workspaces/" + workspaceId + "/probe/member");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {"workspaceId":"%s","membershipId":"%s","userId":"%s","role":"%s",
                 "timezone":"%s","baseCurrency":"%s"}""".formatted(
                        row.get("workspace_id"),
                        row.get("id"),
                        row.get("user_id"),
                        row.get("role"),
                        row.get("timezone"),
                        row.get("base_currency")));
    }

    @Test
    void anonymousGets401() {
        UUID workspaceId = workspaces.createFor(users.create("Ana Teste", "ana@example.com"));

        var result =
                mvc.get().uri("/v1/workspaces/" + workspaceId + "/probe/read").exchange();

        assertThat(result).hasStatus(401).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.required");
    }

    private MvcTestResult get(UUID userId, String uri) {
        return mvc.get().uri(uri).with(TestUsers.signedIn(userId, null)).exchange();
    }

    private MvcTestResult post(UUID userId, String uri) {
        return mvc.post()
                .uri(uri)
                .with(TestUsers.signedIn(userId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(userId, null)))
                .exchange();
    }

    private static void assertForbidden(MvcTestResult result) {
        assertThat(result).hasStatus(403).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
    }

    private static void assertNotFound(MvcTestResult result) {
        assertThat(result).hasStatus(404).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"code":"workspace.not-found","detail":"Família não encontrada."}""");
    }

    /** Family routes with each kind of guard, answering with the role the guard handed them. */
    @RestController
    @RequestMapping("/v1/workspaces/{ws}/probe")
    static class ProbeController {

        @GetMapping("/read")
        @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
        String read(WorkspaceMember member) {
            return member.role().name();
        }

        @PostMapping("/write")
        @WorkspaceScoped(allow = {OWNER, ADULT})
        String write(WorkspaceMember member) {
            return member.role().name();
        }

        @GetMapping("/member")
        @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
        WorkspaceMember member(WorkspaceMember member) {
            return member;
        }

        @GetMapping("/youth")
        @WorkspaceScoped(allow = {OWNER, YOUTH})
        String youth(WorkspaceMember member) {
            return member.role().name();
        }

        @GetMapping("/open")
        String open() {
            return "open";
        }
    }
}
