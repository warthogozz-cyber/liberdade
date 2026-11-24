package br.com.liberdadefamilia.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The person's acceptance of the terms of use and the privacy policy, outside any family. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LegalDocumentsIT {

    private static final String TERMS = "terms-2026-12";
    private static final String PRIVACY = "privacy-2026-12.4";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Test
    void newUserHasPendingDocuments() {
        UUID ana = users.create("Ana Teste", "ana@example.com");

        MvcTestResult result = list(ana);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(2);
        assertThat(result).bodyJson().extractingPath("$[0].purpose").isEqualTo("TERMS");
        assertThat(result).bodyJson().extractingPath("$[0].currentVersion").isEqualTo(TERMS);
        assertThat(result).bodyJson().extractingPath("$[0].acceptedVersion").isNull();
        assertThat(result).bodyJson().extractingPath("$[0].acceptedAt").isNull();
        assertThat(result).bodyJson().extractingPath("$[1].purpose").isEqualTo("PRIVACY");
        assertThat(result).bodyJson().extractingPath("$[1].currentVersion").isEqualTo(PRIVACY);
        assertThat(result).bodyJson().extractingPath("$[1].acceptedVersion").isNull();
    }

    @Test
    void acceptRecordsBoth() {
        UUID ana = users.create("Ana Teste", "ana@example.com");

        assertThat(accept(ana, TERMS, PRIVACY)).hasStatus(204);

        MvcTestResult result = list(ana);
        assertThat(result).bodyJson().extractingPath("$[0].acceptedVersion").isEqualTo(TERMS);
        assertThat(result).bodyJson().extractingPath("$[0].acceptedAt").isNotNull();
        assertThat(result).bodyJson().extractingPath("$[1].acceptedVersion").isEqualTo(PRIVACY);
        assertThat(result).bodyJson().extractingPath("$[1].acceptedAt").isNotNull();
        List<Map<String, Object>> rows = rows(ana);
        assertThat(rows).hasSize(2);
        assertThat(rows)
                .allSatisfy(
                        row -> assertThat(row).containsEntry("granted_by", ana).containsEntry("workspace_id", null));
    }

    @Test
    void acceptTwiceIsNoop() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        assertThat(accept(ana, TERMS, PRIVACY)).hasStatus(204);

        assertThat(accept(ana, TERMS, PRIVACY)).hasStatus(204);

        assertThat(rows(ana)).hasSize(2);
        assertThat(rows(ana)).allSatisfy(row -> assertThat(row).containsEntry("superseded", false));
        assertThat(auditCount(ana)).isOne();
    }

    @Test
    void outdatedVersionRejected() {
        UUID ana = users.create("Ana Teste", "ana@example.com");

        MvcTestResult result = accept(ana, "terms-2026-10", PRIVACY);

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("documents.version-outdated");
        assertThat(rows(ana)).isEmpty();
        assertThat(auditCount(ana)).isZero();
    }

    @Test
    void outdatedPrivacyRejectedToo() {
        UUID ana = users.create("Ana Teste", "ana@example.com");

        MvcTestResult result = accept(ana, TERMS, "privacy-2026-10");

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("documents.version-outdated");
        assertThat(rows(ana)).isEmpty();
        assertThat(auditCount(ana)).isZero();
    }

    @Test
    void concurrentAcceptsRecordOnce() throws Exception {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        int callers = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<MvcTestResult>> calls = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                calls.add(pool.submit(() -> {
                    start.await();
                    return accept(ana, TERMS, PRIVACY);
                }));
            }
            start.countDown();
            for (Future<MvcTestResult> call : calls) {
                assertThat(call.get(30, TimeUnit.SECONDS)).hasStatus(204);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(rows(ana)).hasSize(2);
        assertThat(rows(ana)).allSatisfy(row -> assertThat(row).containsEntry("superseded", false));
        assertThat(auditCount(ana)).isOne();
    }

    @Test
    void blankVersionFailsValidation() {
        UUID ana = users.create("Ana Teste", "ana@example.com");

        MvcTestResult result = accept(ana, "", PRIVACY);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
    }

    @Test
    void newVersionSupersedesOld() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        jdbc.sql("""
                        insert into consents (user_id, workspace_id, purpose, document_version, granted_by)
                        values (:user, null, 'TERMS', 'terms-2026-10', :user)""").param("user", ana).update();

        assertThat(accept(ana, TERMS, PRIVACY)).hasStatus(204);

        List<Map<String, Object>> terms = rows(ana).stream()
                .filter(row -> "TERMS".equals(row.get("purpose")))
                .toList();
        assertThat(terms).hasSize(2);
        assertThat(terms.stream()
                        .filter(row -> "terms-2026-10".equals(row.get("document_version")))
                        .findFirst()
                        .orElseThrow())
                .containsEntry("superseded", true);
        assertThat(terms.stream()
                        .filter(row -> TERMS.equals(row.get("document_version")))
                        .findFirst()
                        .orElseThrow())
                .containsEntry("superseded", false);
        assertThat(list(ana)).bodyJson().extractingPath("$[0].acceptedVersion").isEqualTo(TERMS);
    }

    @Test
    void acceptIsAudited() {
        UUID ana = users.create("Ana Teste", "ana@example.com");

        assertThat(accept(ana, TERMS, PRIVACY)).hasStatus(204);

        MvcTestResult activity =
                mvc.get().uri("/v1/me/activity").with(session(ana)).exchange();
        assertThat(activity).hasStatusOk();
        List<String> actions = JsonPath.read(body(activity), "$[*].action");
        assertThat(actions).contains("documents.accepted");
        assertThat(auditCount(ana)).isOne();
    }

    @Test
    void documentsNotInConsentList() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        assertThat(accept(ana, TERMS, PRIVACY)).hasStatus(204);
        UUID acceptance = jdbc.sql("select id from consents where user_id = :user limit 1")
                .param("user", ana)
                .query(UUID.class)
                .single();

        MvcTestResult consents =
                mvc.get().uri("/v1/me/consents").with(session(ana)).exchange();
        MvcTestResult revoke = mvc.delete()
                .uri("/v1/me/consents/" + acceptance)
                .with(session(ana))
                .with(CsrfSession.start(mvc, session(ana)))
                .exchange();

        assertThat(consents).hasStatusOk();
        assertThat(consents).bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(revoke).hasStatus(404);
        assertThat(revoke).bodyJson().extractingPath("$.code").isEqualTo("consent.not-found");
        assertThat(rows(ana)).allSatisfy(row -> assertThat(row).containsEntry("revoked", false));
    }

    @Test
    void parentalStillNeedsWorkspace() {
        UUID ana = users.create("Ana Teste", "ana@example.com");

        assertThatThrownBy(() -> jdbc.sql("""
                                insert into consents (user_id, workspace_id, purpose, document_version, granted_by)
                                values (:user, null, 'PARENTAL', 'x', :user)""").param("user", ana).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void termsNeverBelongToAFamily() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);

        assertThatThrownBy(
                        () -> jdbc.sql("""
                                insert into consents (user_id, workspace_id, purpose, document_version, granted_by)
                                values (:user, :ws, 'TERMS', 'x', :user)""").param("user", ana).param("ws", ws).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueIndexRejectsTwoOpenAcceptances() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        String insert = """
                insert into consents (user_id, workspace_id, purpose, document_version, granted_by)
                values (:user, null, 'PRIVACY', 'x', :user)""";
        jdbc.sql(insert).param("user", ana).update();

        assertThatThrownBy(() -> jdbc.sql(insert).param("user", ana).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anonymousGets401() {
        assertThat(mvc.get().uri("/v1/me/documents").exchange()).hasStatus(401);
        assertThat(mvc.post()
                        .uri("/v1/me/documents/accept")
                        .with(CsrfSession.start(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(TERMS, PRIVACY))
                        .exchange())
                .hasStatus(401);
    }

    // --- helpers ---

    private MvcTestResult list(UUID user) {
        return mvc.get().uri("/v1/me/documents").with(session(user)).exchange();
    }

    private MvcTestResult accept(UUID user, String terms, String privacy) {
        return mvc.post()
                .uri("/v1/me/documents/accept")
                .with(session(user))
                .with(CsrfSession.start(mvc, session(user)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(terms, privacy))
                .exchange();
    }

    private static String json(String terms, String privacy) {
        return "{\"terms\":\"" + terms + "\",\"privacy\":\"" + privacy + "\"}";
    }

    private List<Map<String, Object>> rows(UUID user) {
        return jdbc.sql("""
                        select workspace_id, purpose, document_version, granted_by,
                               revoked_at is not null as revoked, superseded_at is not null as superseded
                        from consents where user_id = :user order by granted_at, id""").param("user", user).query().listOfRows();
    }

    private long auditCount(UUID user) {
        return jdbc.sql(
                        "select count(*) from audit_events where actor_user_id = :user and action = 'documents.accepted'")
                .param("user", user)
                .query(Long.class)
                .single();
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
