package br.com.liberdadefamilia.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** With billing on (the test profile), the texts from before billing are no longer the ones in force. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DocumentsBillingIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Test
    void acceptOldVersionIsOutdated() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        RequestPostProcessor session = TestUsers.signedIn(ana, null);

        MvcTestResult result = mvc.post()
                .uri("/v1/me/documents/accept")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"terms\":\"terms-2026-11\",\"privacy\":\"privacy-2026-11\"}")
                .exchange();

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("documents.version-outdated");
        assertThat(jdbc.sql("select count(*) from consents where user_id = :user")
                        .param("user", ana)
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void userWhoAcceptedNovemberMustAcceptAgain() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        jdbc.sql("""
                        insert into consents (user_id, workspace_id, purpose, document_version, granted_by)
                        values (:user, null, 'TERMS', 'terms-2026-11', :user),
                               (:user, null, 'PRIVACY', 'privacy-2026-11', :user)""").param("user", ana).update();

        MvcTestResult result = mvc.get()
                .uri("/v1/me/documents")
                .with(TestUsers.signedIn(ana, null))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[0].purpose").isEqualTo("TERMS");
        assertThat(result).bodyJson().extractingPath("$[0].currentVersion").isEqualTo("terms-2026-12");
        assertThat(result).bodyJson().extractingPath("$[0].acceptedVersion").isEqualTo("terms-2026-11");
        assertThat(result).bodyJson().extractingPath("$[1].purpose").isEqualTo("PRIVACY");
        assertThat(result).bodyJson().extractingPath("$[1].currentVersion").isEqualTo("privacy-2026-12.2");
        assertThat(result).bodyJson().extractingPath("$[1].acceptedVersion").isEqualTo("privacy-2026-11");
        // The terms changed, and the policy of November is not in the chain of the December one, so both ask.
        assertThat(result).bodyJson().extractingPath("$[0].acceptanceRequired").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$[1].acceptanceRequired").isEqualTo(true);
    }

    @Test
    void billingPolicyKeepsDecemberAcceptance() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        jdbc.sql("""
                        insert into consents (user_id, workspace_id, purpose, document_version, granted_by)
                        values (:user, null, 'TERMS', 'terms-2026-12', :user),
                               (:user, null, 'PRIVACY', 'privacy-2026-12', :user)""").param("user", ana).update();

        MvcTestResult result = mvc.get()
                .uri("/v1/me/documents")
                .with(TestUsers.signedIn(ana, null))
                .exchange();

        assertThat(result).bodyJson().extractingPath("$[1].currentVersion").isEqualTo("privacy-2026-12.2");
        assertThat(result).bodyJson().extractingPath("$[1].acceptanceRequired").isEqualTo(false);
    }

    @Test
    void chainKeepsAcceptance() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        jdbc.sql("""
                        insert into consents (user_id, workspace_id, purpose, document_version, granted_by)
                        values (:user, null, 'PRIVACY', 'privacy-2026-12', :user)""").param("user", ana).update();

        MvcTestResult result = mvc.get()
                .uri("/v1/me/documents")
                .with(TestUsers.signedIn(ana, null))
                .exchange();

        assertThat(result).bodyJson().extractingPath("$[1].currentVersion").isEqualTo("privacy-2026-12.2");
        assertThat(result).bodyJson().extractingPath("$[1].acceptanceRequired").isEqualTo(false);
    }

    @Test
    void chainKeepsAcceptanceThroughTheFirstAddition() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        jdbc.sql("""
                        insert into consents (user_id, workspace_id, purpose, document_version, granted_by)
                        values (:user, null, 'PRIVACY', 'privacy-2026-12.1', :user)""").param("user", ana).update();

        MvcTestResult result = mvc.get()
                .uri("/v1/me/documents")
                .with(TestUsers.signedIn(ana, null))
                .exchange();

        assertThat(result).bodyJson().extractingPath("$[1].currentVersion").isEqualTo("privacy-2026-12.2");
        assertThat(result).bodyJson().extractingPath("$[1].acceptanceRequired").isEqualTo(false);
    }

    /** Before billing, the .2 policy continues the .1 and the November ones and keeps their acceptance. */
    @Nested
    @TestPropertySource(properties = "liberdade.billing.enabled=false")
    class BillingOff {

        @Autowired
        MockMvcTester mvc;

        @Autowired
        JdbcClient jdbc;

        @Autowired
        TestUsers users;

        @Test
        void chainKeepsAcceptanceForNovemberSet() {
            UUID ana = users.create("Ana Teste", "ana@example.com");
            jdbc.sql("""
                            insert into consents (user_id, workspace_id, purpose, document_version, granted_by)
                            values (:user, null, 'PRIVACY', 'privacy-2026-11', :user)""").param("user", ana).update();

            MvcTestResult result = mvc.get()
                    .uri("/v1/me/documents")
                    .with(TestUsers.signedIn(ana, null))
                    .exchange();

            assertThat(result).bodyJson().extractingPath("$[1].currentVersion").isEqualTo("privacy-2026-11.2");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[1].acceptanceRequired")
                    .isEqualTo(false);
        }

        @Test
        void oldAcceptanceStillCounts() {
            UUID ana = users.create("Ana Teste", "ana@example.com");
            jdbc.sql("""
                            insert into consents (user_id, workspace_id, purpose, document_version, granted_by)
                            values (:user, null, 'TERMS', 'terms-2026-11', :user),
                                   (:user, null, 'PRIVACY', 'privacy-2026-11', :user)""").param("user", ana).update();

            MvcTestResult result = mvc.get()
                    .uri("/v1/me/documents")
                    .with(TestUsers.signedIn(ana, null))
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$[1].currentVersion").isEqualTo("privacy-2026-11.2");
            assertThat(result).bodyJson().extractingPath("$[1].acceptedVersion").isEqualTo("privacy-2026-11");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[1].acceptanceRequired")
                    .isEqualTo(false);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[0].acceptanceRequired")
                    .isEqualTo(false);
        }

        @Test
        void neverAcceptedRequires() {
            UUID ana = users.create("Ana Teste", "ana@example.com");

            MvcTestResult result = mvc.get()
                    .uri("/v1/me/documents")
                    .with(TestUsers.signedIn(ana, null))
                    .exchange();

            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[0].acceptanceRequired")
                    .isEqualTo(true);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$[1].acceptanceRequired")
                    .isEqualTo(true);
        }

        @Test
        void acceptTakesNewVersion() {
            UUID ana = users.create("Ana Teste", "ana@example.com");
            RequestPostProcessor session = TestUsers.signedIn(ana, null);

            MvcTestResult current = accept(session, "terms-2026-11", "privacy-2026-11.2");
            MvcTestResult old = accept(session, "terms-2026-11", "privacy-2026-11");

            assertThat(current).hasStatus(204);
            assertThat(old).hasStatus(409);
            assertThat(old).bodyJson().extractingPath("$.code").isEqualTo("documents.version-outdated");
        }

        private MvcTestResult accept(RequestPostProcessor session, String terms, String privacy) {
            return mvc.post()
                    .uri("/v1/me/documents/accept")
                    .with(session)
                    .with(CsrfSession.start(mvc, session))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"terms\":\"" + terms + "\",\"privacy\":\"" + privacy + "\"}")
                    .exchange();
        }
    }
}
