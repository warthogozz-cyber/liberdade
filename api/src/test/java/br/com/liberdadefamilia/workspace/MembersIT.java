package br.com.liberdadefamilia.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MembersIT {

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

    @Test
    void listsMembersWithTheCallerMarked() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.ADULT, "Bibi");

        MvcTestResult result = get(ana, ws);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(2);
        assertThat(result).bodyJson().extractingPath("$[0].you").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$[0].role").isEqualTo("OWNER");
        assertThat(result).bodyJson().extractingPath("$[0].passkeyOnly").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$[1].nickname").isEqualTo("Bibi");
        assertThat(result).bodyJson().extractingPath("$[1].you").isEqualTo(false);
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
    void roleChangeNeedsRecentSignIn() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);

        MvcTestResult stale = patch(ana, ws, biaMembership, "{\"role\":\"VIEWER\"}", 30);
        assertThat(stale).hasStatus(403);
        assertThat(stale).bodyJson().extractingPath("$.code").isEqualTo("auth.reauthentication-required");

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
    void anotherOwnerMakesStepDownPossible() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.OWNER);

        assertThat(patch(ana, ws, workspaces.membershipOf(ws, ana), "{\"role\":\"ADULT\"}", 0))
                .hasStatusOk();
    }

    @Test
    void youthNeedsParentalConsent() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID teo = users.create("Teo Teste", "teo@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID teoMembership = workspaces.addMember(ws, teo, Role.ADULT);

        MvcTestResult without = patch(ana, ws, teoMembership, "{\"role\":\"YOUTH\"}", 0);
        assertThat(without).hasStatus(422);
        assertThat(without).bodyJson().extractingPath("$.code").isEqualTo("member.parental-consent-required");

        MvcTestResult with = patch(ana, ws, teoMembership, "{\"role\":\"YOUTH\",\"parentalConsent\":true}", 0);
        assertThat(with).hasStatusOk();
        assertThat(with).bodyJson().extractingPath("$.role").isEqualTo("YOUTH");
    }

    @Test
    void emptyPatchIs422() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);

        assertThat(patch(ana, ws, workspaces.membershipOf(ws, ana), "{}", 0)).hasStatus(422);
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
    void removeEndsTheMembershipAndIsIdempotent() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT);

        assertThat(delete(ana, ws, biaMembership, 30)).hasStatus(403);
        assertThat(delete(ana, ws, biaMembership, 0)).hasStatus(204);
        assertThat(delete(ana, ws, biaMembership, 0)).hasStatus(204);

        assertThat(memberships.isActive(ws, biaMembership)).isFalse();
        assertThat(memberships.find(ws, biaMembership))
                .get()
                .extracting(MemberSummary::status)
                .isEqualTo(MembershipStatus.REMOVED);
    }

    @Test
    void renameByOwnerOnly() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.ADULT);

        MvcTestResult denied = rename(bia, ws, "{\"name\":\"Outra\"}");
        MvcTestResult ok = rename(ana, ws, "{\"name\":\"  Os Silva  \"}");
        MvcTestResult blank = rename(ana, ws, "{\"name\":\"   \"}");

        assertThat(denied).hasStatus(403);
        assertThat(ok).hasStatusOk();
        assertThat(ok).bodyJson().extractingPath("$.name").isEqualTo("Os Silva");
        assertThat(blank).hasStatus(422);
        assertThat(blank).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
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
