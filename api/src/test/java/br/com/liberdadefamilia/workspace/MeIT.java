package br.com.liberdadefamilia.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MeIT {

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
    void meWithoutFamily() {
        UUID userId = users.create("Ana Teste", "ana@example.com");

        var result = me(userId, "Teste");

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"user":{"id":"%s","displayName":"Ana Teste","passkeyCreatedAt":null},
                 "workspaces":[],
                 "suggestedWorkspaceName":"Família Teste"}""".formatted(userId));
    }

    @Test
    void meWithoutFamilyName() {
        UUID userId = users.create("Ana Teste", "ana@example.com");

        var result = me(userId, null);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.suggestedWorkspaceName").isEqualTo("Minha família");
    }

    @Test
    void meWithFamily() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        UUID workspaceId = workspaces.createFor(userId);
        UUID membershipId = workspaces.membershipOf(workspaceId, userId);

        var result = me(userId, "Teste");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"workspaces":[{"id":"%s","name":"Família Teste","role":"OWNER","membershipId":"%s",
                                "timezone":"America/Fortaleza","currency":"BRL"}]}""".formatted(workspaceId, membershipId));
    }

    @Test
    void meHidesRemovedMembership() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        UUID workspaceId = workspaces.createFor(userId);
        jdbc.sql("update memberships set status = 'REMOVED' where id = :id")
                .param("id", workspaces.membershipOf(workspaceId, userId))
                .update();

        var result = me(userId, "Teste");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.workspaces").asArray().isEmpty();
    }

    @Test
    void meHidesFamilyBeingDeleted() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        UUID workspaceId = workspaces.createFor(userId);
        jdbc.sql("update workspaces set deletion_requested_at = now() where id = :id")
                .param("id", workspaceId)
                .update();

        var result = me(userId, "Teste");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.workspaces").asArray().isEmpty();
    }

    @Test
    void meShowsPasskeyDate() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        Bytes handle = Bytes.random();
        userEntities.save(ImmutablePublicKeyCredentialUserEntity.builder()
                .id(handle)
                .name(userId.toString())
                .displayName("Ana Teste")
                .build());
        Instant created = Instant.parse("2026-11-02T10:00:00Z");
        userCredentials.save(ImmutableCredentialRecord.builder()
                .credentialId(Bytes.random())
                .userEntityUserId(handle)
                .publicKey(new ImmutablePublicKeyCose(new byte[] {1, 2, 3}))
                .transports(Set.of())
                .label("Celular")
                .created(created)
                .lastUsed(created)
                .build());

        var result = me(userId, null);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.user.passkeyCreatedAt").isEqualTo("2026-11-02T10:00:00Z");
    }

    private MvcTestResult me(UUID userId, @Nullable String familyName) {
        return mvc.get()
                .uri("/v1/me")
                .with(TestUsers.signedIn(userId, familyName))
                .exchange();
    }
}
