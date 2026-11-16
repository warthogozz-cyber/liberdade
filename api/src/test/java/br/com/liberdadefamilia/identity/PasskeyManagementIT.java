package br.com.liberdadefamilia.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Listing and removing the signed-in person's own passkeys, and never leaving them without a way back in. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasskeyManagementIT {

    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    PublicKeyCredentialUserEntityRepository userEntities;

    @Autowired
    UserCredentialRepository userCredentials;

    @Test
    void listsOwnPasskeysNewestFirst() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        Bytes older = passkey(userId, "Celular", Instant.parse("2026-11-01T10:00:00Z"));
        Bytes newer = passkey(userId, "Notebook", Instant.parse("2026-11-02T10:00:00Z"));

        MvcTestResult result = list(userId);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(2);
        assertThat(result).bodyJson().extractingPath("$[0].id").isEqualTo(newer.toBase64UrlString());
        assertThat(result).bodyJson().extractingPath("$[0].label").isEqualTo("Notebook");
        assertThat(result).bodyJson().extractingPath("$[0].createdAt").isEqualTo("2026-11-02T10:00:00Z");
        assertThat(result).bodyJson().extractingPath("$[1].id").isEqualTo(older.toBase64UrlString());
        assertThat(result).bodyJson().extractingPath("$[1].label").isEqualTo("Celular");
    }

    @Test
    void otherPeoplesPasskeysHidden() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        passkey(ana, "Celular", Instant.parse("2026-11-01T10:00:00Z"));
        Bytes biasKey = passkey(bia, "Tablet", Instant.parse("2026-11-01T10:00:00Z"));
        google(ana);

        assertThat(list(ana)).bodyJson().extractingPath("$").asArray().hasSize(1);
        MvcTestResult result = delete(ana, biasKey.toBase64UrlString(), 0);

        assertThat(result).hasStatus(404).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("passkey.not-found");
        assertThat(count(bia)).isEqualTo(1);
    }

    @Test
    void removeNeedsRecentSignIn() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        Bytes id = passkey(userId, "Celular", Instant.parse("2026-11-01T10:00:00Z"));
        google(userId);

        MvcTestResult result = delete(userId, id.toBase64UrlString(), 11);

        assertThat(result).hasStatus(403).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.reauthentication-required");
        assertThat(count(userId)).isEqualTo(1);
    }

    @Test
    void removeWithGoogleIdentity() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        Bytes id = passkey(userId, "Celular", Instant.parse("2026-11-01T10:00:00Z"));
        google(userId);

        assertThat(delete(userId, id.toBase64UrlString(), 0)).hasStatus(204);

        assertThat(list(userId)).bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void lastPasskeyWithoutGoogleRefused() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID teo = users.create("Teo Teste", null);
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, teo, Role.YOUTH, "Teco");
        Bytes id = passkey(teo, "Celular", Instant.parse("2026-11-01T10:00:00Z"));

        MvcTestResult result = delete(teo, id.toBase64UrlString(), 0);

        assertThat(result).hasStatus(422).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("passkey.last-method");
        assertThat(count(teo)).isEqualTo(1);
    }

    @Test
    void secondPasskeyWithoutGoogleRemovable() {
        UUID teo = users.create("Teo Teste", null);
        Bytes first = passkey(teo, "Celular", Instant.parse("2026-11-01T10:00:00Z"));
        passkey(teo, "Notebook", Instant.parse("2026-11-02T10:00:00Z"));

        assertThat(delete(teo, first.toBase64UrlString(), 0)).hasStatus(204);

        assertThat(count(teo)).isEqualTo(1);
    }

    @Test
    void removeIsAudited() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        Bytes id = passkey(userId, "Celular", Instant.parse("2026-11-01T10:00:00Z"));
        google(userId);

        assertThat(delete(userId, id.toBase64UrlString(), 0)).hasStatus(204);

        MvcTestResult activity = mvc.get()
                .uri("/v1/me/activity")
                .with(TestUsers.signedIn(userId, null))
                .exchange();
        assertThat(activity).hasStatusOk();
        assertThat(activity).bodyJson().extractingPath("$[0].action").isEqualTo("passkey.removed");
        assertThat(activity).bodyJson().extractingPath("$[0].byMe").isEqualTo(true);
    }

    @Test
    void sessionSurvivesRemoval() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        Bytes id = passkey(userId, "Celular", Instant.parse("2026-11-01T10:00:00Z"));
        google(userId);
        RequestPostProcessor session = TestUsers.signedIn(userId, null);
        CsrfSession csrf = CsrfSession.start(mvc, session);

        assertThat(mvc.delete()
                        .uri("/v1/me/passkeys/" + id.toBase64UrlString())
                        .with(session)
                        .with(csrf)
                        .exchange())
                .hasStatus(204);

        assertThat(mvc.get().uri("/v1/me").with(session).with(csrf).exchange()).hasStatusOk();
    }

    private Bytes passkey(UUID userId, String label, Instant created) {
        PublicKeyCredentialUserEntity existing = userEntities.findByUsername(userId.toString());
        Bytes handle = existing != null ? existing.getId() : saveEntity(userId);
        Bytes credentialId = Bytes.random();
        userCredentials.save(ImmutableCredentialRecord.builder()
                .credentialId(credentialId)
                .userEntityUserId(handle)
                .publicKey(new ImmutablePublicKeyCose(new byte[] {1, 2, 3}))
                .transports(Set.of())
                .label(label)
                .created(created)
                .lastUsed(created)
                .build());
        return credentialId;
    }

    private Bytes saveEntity(UUID userId) {
        Bytes handle = Bytes.random();
        userEntities.save(ImmutablePublicKeyCredentialUserEntity.builder()
                .id(handle)
                .name(userId.toString())
                .displayName("Teste")
                .build());
        return handle;
    }

    private void google(UUID userId) {
        jdbc.sql(
                        "insert into auth_identities (user_id, provider, subject, last_used_at) values (:u, 'google', :s, now())")
                .param("u", userId)
                .param("s", "sub-" + userId)
                .update();
    }

    private int count(UUID userId) {
        return jdbc.sql("""
                        select count(*) from user_credentials c
                          join user_entities e on e.id = c.user_entity_user_id
                         where e.name = :name""")
                .param("name", userId.toString())
                .query(Integer.class)
                .single();
    }

    private MvcTestResult list(UUID userId) {
        return mvc.get()
                .uri("/v1/me/passkeys")
                .with(TestUsers.signedIn(userId, null))
                .exchange();
    }

    private MvcTestResult delete(UUID userId, String id, int minutesAgo) {
        RequestPostProcessor session =
                minutesAgo == 0 ? TestUsers.signedIn(userId, null) : TestUsers.signedInMinutesAgo(userId, minutesAgo);
        return mvc.delete()
                .uri("/v1/me/passkeys/" + id)
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();
    }
}
