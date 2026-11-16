package br.com.liberdadefamilia.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

/** The parental consent a guardian gives for a youth: recorded on entry, listed for both sides, revoked by the giver. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConsentsIT {

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
    TestLedger ledger;

    @Test
    void youthJoinRecordsParentalConsent() {
        Family family = familyWithYouth();

        List<Map<String, Object>> rows = consentRows(family.ws);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst())
                .containsEntry("user_id", family.leo)
                .containsEntry("granted_by", family.ana)
                .containsEntry("purpose", "PARENTAL")
                .containsEntry("document_version", "parental-2026-11")
                .containsEntry("revoked", false);
        assertThat(actions(family.ana)).contains("consent.granted");
        assertThat(actions(family.leo)).contains("consent.granted");
    }

    @Test
    void roleChangeToYouthRecordsConsent() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT, "Bibi");

        assertThat(patch(ana, ws, biaMembership, "{\"role\":\"YOUTH\",\"parentalConsent\":true}"))
                .hasStatusOk();

        List<Map<String, Object>> rows = consentRows(ws);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst())
                .containsEntry("user_id", bia)
                .containsEntry("granted_by", ana)
                .containsEntry("purpose", "PARENTAL");
    }

    @Test
    void otherRoleChangesRecordNothing() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT, "Bibi");

        assertThat(patch(ana, ws, biaMembership, "{\"role\":\"VIEWER\"}")).hasStatusOk();

        assertThat(consentRows(ws)).isEmpty();
    }

    @Test
    void bothSidesSeeConsent() {
        Family family = familyWithYouth();

        MvcTestResult forAna = list(family.ana);
        assertThat(forAna).hasStatusOk();
        assertThat(forAna).bodyJson().extractingPath("$").asArray().hasSize(1);
        assertThat(forAna).bodyJson().extractingPath("$[0].canRevoke").isEqualTo(true);
        assertThat(forAna).bodyJson().extractingPath("$[0].purpose").isEqualTo("PARENTAL");
        assertThat(forAna).bodyJson().extractingPath("$[0].documentVersion").isEqualTo("parental-2026-11");
        assertThat(forAna).bodyJson().extractingPath("$[0].workspaceName").isEqualTo("Família Teste");
        assertThat(forAna).bodyJson().extractingPath("$[0].subjectName").isEqualTo("Leo");
        assertThat(forAna).bodyJson().extractingPath("$[0].grantedByName").isEqualTo("Aninha");
        assertThat(forAna).bodyJson().extractingPath("$[0].revokedAt").isNull();

        MvcTestResult forLeo = list(family.leo);
        assertThat(forLeo).bodyJson().extractingPath("$").asArray().hasSize(1);
        assertThat(forLeo).bodyJson().extractingPath("$[0].canRevoke").isEqualTo(false);
        assertThat(forLeo).bodyText().doesNotContain("ana@example.com");
    }

    @Test
    void listIsUnauthenticated401() {
        assertThat(mvc.get().uri("/v1/me/consents").exchange()).hasStatus(401);
    }

    @Test
    void namesAreNullOnceTheAccountIsGone() {
        Family family = familyWithYouth();
        // What the foreign key does when the guardian's account goes: granted_by is cleared.
        jdbc.sql("update consents set granted_by = null where workspace_id = :ws")
                .param("ws", family.ws)
                .update();

        MvcTestResult forLeo = list(family.leo);

        assertThat(forLeo).bodyJson().extractingPath("$[0].grantedByName").isNull();
        assertThat(forLeo).bodyJson().extractingPath("$[0].subjectName").isEqualTo("Leo");
    }

    @Test
    void onlyGuardianRevokes() {
        Family family = familyWithYouth();
        UUID bia = users.create("Bia Teste", "bia@example.com");
        workspaces.addMember(family.ws, bia, Role.ADULT, "Bibi");
        UUID consent = consentId(family.ws);

        MvcTestResult byYouth = revoke(family.leo, consent);
        assertThat(byYouth).hasStatus(403);
        assertThat(byYouth).bodyJson().extractingPath("$.code").isEqualTo("consent.revoke-forbidden");
        assertThat(byYouth)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Só quem deu o consentimento pode revogá-lo.");
        MvcTestResult byOther = revoke(bia, consent);
        assertThat(byOther).hasStatus(404);
        assertThat(byOther).bodyJson().extractingPath("$.code").isEqualTo("consent.not-found");
        assertThat(byOther).bodyJson().extractingPath("$.detail").isEqualTo("Consentimento não encontrado.");
        assertThat(revoke(family.ana, UUID.randomUUID())).hasStatus(404);
        assertThat(consentRows(family.ws).getFirst()).containsEntry("revoked", false);
    }

    @Test
    void revokeRemovesYouth() {
        Family family = familyWithYouth();
        UUID leoMembership = workspaces.membershipOf(family.ws, family.leo);
        ledger.expense(
                family.ws,
                leoMembership,
                ledger.categoryOf(family.ws, "EXPENSE", "Mercado"),
                "12.50",
                LocalDate.of(2026, 11, 10));

        assertThat(revoke(family.ana, consentId(family.ws))).hasStatus(204);

        assertThat(statusOf(leoMembership)).isEqualTo("REMOVED");
        assertThat(jdbc.sql("select count(*) from transactions where workspace_id = :ws and member_id = :member")
                        .param("ws", family.ws)
                        .param("member", leoMembership)
                        .query(Long.class)
                        .single())
                .isOne();
        MvcTestResult me = mvc.get().uri("/v1/me").with(session(family.leo)).exchange();
        assertThat(me).hasStatusOk();
        assertThat(me).bodyJson().extractingPath("$.workspaces").asArray().isEmpty();
        assertThat(list(family.ana)).bodyJson().extractingPath("$[0].canRevoke").isEqualTo(false);
    }

    @Test
    void revokeIsAudited() {
        Family family = familyWithYouth();

        assertThat(revoke(family.ana, consentId(family.ws))).hasStatus(204);

        assertThat(actions(family.ana)).contains("consent.revoked");
        assertThat(actions(family.leo)).contains("consent.revoked");
    }

    @Test
    void revokeTwiceIsNoop() {
        Family family = familyWithYouth();
        UUID consent = consentId(family.ws);
        assertThat(revoke(family.ana, consent)).hasStatus(204);
        Object revokedAt = consentRows(family.ws).getFirst().get("revoked_at");
        long audited = revokedAudits(family.ws);

        assertThat(revoke(family.ana, consent)).hasStatus(204);

        assertThat(consentRows(family.ws).getFirst()).containsEntry("revoked_at", revokedAt);
        assertThat(revokedAudits(family.ws)).isEqualTo(audited).isOne();
    }

    @Test
    void revokeAfterPromotionKeepsMember() {
        Family family = familyWithYouth();
        UUID leoMembership = workspaces.membershipOf(family.ws, family.leo);
        assertThat(patch(family.ana, family.ws, leoMembership, "{\"role\":\"ADULT\"}"))
                .hasStatusOk();

        assertThat(revoke(family.ana, consentId(family.ws))).hasStatus(204);

        assertThat(statusOf(leoMembership)).isEqualTo("ACTIVE");
        assertThat(consentRows(family.ws).getFirst()).containsEntry("revoked", true);
    }

    @Test
    void revokeNeedsAnActiveGuardian() {
        Family family = familyWithYouth();
        jdbc.sql("update memberships set role = 'VIEWER' where workspace_id = :ws and user_id = :user")
                .param("ws", family.ws)
                .param("user", family.ana)
                .update();

        MvcTestResult list = list(family.ana);
        assertThat(list).bodyJson().extractingPath("$[0].canRevoke").isEqualTo(false);
        MvcTestResult result = revoke(family.ana, consentId(family.ws));

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("consent.revoke-forbidden");
        assertThat(consentRows(family.ws).getFirst()).containsEntry("revoked", false);
    }

    // --- helpers ---

    private record Family(UUID ws, UUID ana, UUID leo) {}

    private Family familyWithYouth() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", "leo@example.com");
        UUID ws = workspaces.createFor(ana);
        jdbc.sql("update memberships set nickname = 'Aninha' where workspace_id = :ws and user_id = :user")
                .param("ws", ws)
                .param("user", ana)
                .update();
        MvcTestResult created = mvc.post()
                .uri("/v1/workspaces/" + ws + "/invitations")
                .with(session(ana))
                .with(CsrfSession.start(mvc, session(ana)))
                .contentType(JSON)
                .content("{\"role\":\"YOUTH\",\"nickname\":\"Leo\",\"parentalConsent\":true}")
                .exchange();
        assertThat(created).hasStatus(201);
        String token = JsonPath.read(body(created), "$.token");
        String address = "10.9.0." + (NEXT_ADDRESS.incrementAndGet() % 250 + 1);
        MvcTestResult accepted = mvc.post()
                .uri("/v1/invitations/accept")
                .with(session(leo))
                .with(CsrfSession.start(mvc, session(leo)))
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .contentType(JSON)
                .content("{\"token\":\"" + token + "\"}")
                .exchange();
        assertThat(accepted).hasStatusOk();
        return new Family(ws, ana, leo);
    }

    private List<Map<String, Object>> consentRows(UUID ws) {
        return jdbc.sql("""
                        select user_id, granted_by, purpose, document_version, revoked_at,
                               revoked_at is not null as revoked
                        from consents where workspace_id = :ws order by granted_at, id""").param("ws", ws).query().listOfRows();
    }

    private UUID consentId(UUID ws) {
        return jdbc.sql("select id from consents where workspace_id = :ws")
                .param("ws", ws)
                .query(UUID.class)
                .single();
    }

    private String statusOf(UUID membershipId) {
        return jdbc.sql("select status from memberships where id = :id")
                .param("id", membershipId)
                .query(String.class)
                .single();
    }

    private long revokedAudits(UUID ws) {
        return jdbc.sql("select count(*) from audit_events where workspace_id = :ws and action = 'consent.revoked'")
                .param("ws", ws)
                .query(Long.class)
                .single();
    }

    private List<String> actions(UUID userId) {
        MvcTestResult result =
                mvc.get().uri("/v1/me/activity").with(session(userId)).exchange();
        assertThat(result).hasStatusOk();
        return JsonPath.read(body(result), "$[*].action");
    }

    private MvcTestResult list(UUID userId) {
        return mvc.get().uri("/v1/me/consents").with(session(userId)).exchange();
    }

    private MvcTestResult revoke(UUID userId, UUID consentId) {
        return mvc.delete()
                .uri("/v1/me/consents/" + consentId)
                .with(session(userId))
                .with(CsrfSession.start(mvc, session(userId)))
                .exchange();
    }

    private MvcTestResult patch(UUID caller, UUID ws, UUID membershipId, String json) {
        return mvc.patch()
                .uri("/v1/workspaces/" + ws + "/members/" + membershipId)
                .with(session(caller))
                .with(CsrfSession.start(mvc, session(caller)))
                .contentType(JSON)
                .content(json)
                .exchange();
    }

    private static RequestPostProcessor session(UUID userId) {
        return TestUsers.signedIn(userId, null);
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
