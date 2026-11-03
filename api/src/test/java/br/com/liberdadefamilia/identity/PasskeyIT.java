package br.com.liberdadefamilia.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.SoftwareAuthenticator;
import br.com.liberdadefamilia.support.TestUsers;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
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
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasskeyIT {

    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");
    private static final String WEB_ORIGIN = "http://localhost:5173";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    Passkeys passkeys;

    @Autowired
    PublicKeyCredentialUserEntityRepository userEntities;

    @Autowired
    UserCredentialRepository userCredentials;

    @Test
    void registerOptionsUseRelyingPartyAndFriendlyName() {
        UUID userId = users.create("Ana Teste", "ana@example.com");

        var result = registerOptions(userId, CsrfSession.start(mvc, TestUsers.signedIn(userId, null)));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.rp.id").isEqualTo("localhost");
        assertThat(result).bodyJson().extractingPath("$.rp.name").isEqualTo("Liberdade");
        assertThat(result).bodyJson().extractingPath("$.user.name").isEqualTo("ana@example.com");
        assertThat(result).bodyJson().extractingPath("$.user.displayName").isEqualTo("Ana Teste");
        String handle = JsonPath.read(body(result), "$.user.id");
        assertThat(jdbc.sql("select name from user_entities where id = :id")
                        .param("id", handle)
                        .query(String.class)
                        .single())
                .isEqualTo(userId.toString());
    }

    @Test
    void registerOptionsKeepTheSameHandle() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        var csrf = CsrfSession.start(mvc, TestUsers.signedIn(userId, null));

        String first = JsonPath.read(body(registerOptions(userId, csrf)), "$.user.id");
        String second = JsonPath.read(body(registerOptions(userId, csrf)), "$.user.id");

        assertThat(second).isEqualTo(first);
        assertThat(jdbc.sql("select count(*) from user_entities where name = :name")
                        .param("name", userId.toString())
                        .query(Long.class)
                        .single())
                .isOne();
    }

    @Test
    void registerOptionsRequireSignIn() {
        var result = mvc.post()
                .uri("/webauthn/register/options")
                .with(CsrfSession.start(mvc))
                .exchange();

        assertThat(result).hasStatus(401).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.required");
    }

    @Test
    void authenticateOptionsAreAnonymous() {
        var result = mvc.post()
                .uri("/webauthn/authenticate/options")
                .with(CsrfSession.start(mvc))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.rpId").isEqualTo("localhost");
        assertThat(result).bodyJson().extractingPath("$.challenge").asString().isNotEmpty();
    }

    @Test
    void registrationPageIsDisabled() {
        UUID userId = users.create("Ana Teste", "ana@example.com");

        assertThat(mvc.get().uri("/webauthn/register").with(TestUsers.signedIn(userId, null)))
                .hasStatus(404);
    }

    @Test
    void passkeysReportsLatestCreation() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        UUID otherId = users.create("Bia Teste", "bia@example.com");
        Bytes handle = Bytes.random();
        userEntities.save(ImmutablePublicKeyCredentialUserEntity.builder()
                .id(handle)
                .name(userId.toString())
                .displayName("Ana Teste")
                .build());
        userCredentials.save(credential(handle, Instant.parse("2026-11-01T10:00:00Z")));
        userCredentials.save(credential(handle, Instant.parse("2026-11-02T10:00:00Z")));

        assertThat(passkeys.latestCreatedAt(userId)).contains(Instant.parse("2026-11-02T10:00:00Z"));
        assertThat(passkeys.latestCreatedAt(otherId)).isEmpty();
    }

    /**
     * The whole ceremony, with a software authenticator: register a passkey while signed in with Google, then sign in
     * with it from a new anonymous session, which must come out under a new id and last as long as a Google one.
     */
    @Test
    void passkeySignInRotatesTheSessionAndKeepsItFor30Days() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        var authenticator = new SoftwareAuthenticator("localhost", WEB_ORIGIN);
        var google = CsrfSession.start(mvc, TestUsers.signedIn(userId, null));
        var registration = mvc.post()
                .uri("/webauthn/register")
                .with(TestUsers.signedIn(userId, null))
                .with(google)
                .contentType(MediaType.APPLICATION_JSON)
                .content(authenticator.register(body(registerOptions(userId, google)), "Celular"))
                .exchange();
        assertThat(registration).hasStatusOk();

        var anonymous = CsrfSession.start(mvc);
        var options =
                mvc.post().uri("/webauthn/authenticate/options").with(anonymous).exchange();
        assertThat(options).hasStatusOk();
        var login = mvc.post()
                .uri("/login/webauthn")
                .with(anonymous)
                .contentType(MediaType.APPLICATION_JSON)
                .content(authenticator.authenticate(body(options)))
                .exchange();

        assertThat(login).hasStatusOk();
        assertThat(login).bodyJson().isStrictlyEqualTo("{\"redirectUrl\":\"/\",\"authenticated\":true}");
        Cookie session = login.getResponse().getCookie(CsrfSession.SESSION_COOKIE);
        assertThat(session).isNotNull();
        String sessionId = CsrfSession.sessionIdOf(session);
        assertThat(sessionId).isNotEqualTo(anonymous.sessionId());
        assertThat(sessionRow(sessionId))
                .containsEntry("max_inactive_interval", 2_592_000)
                .containsEntry("principal_name", userId.toString());
        assertThat(jdbc.sql("select count(*) from spring_session where session_id = :id")
                        .param("id", anonymous.sessionId())
                        .query(Long.class)
                        .single())
                .isZero();

        // The session is signed in as the same person: register options work with it alone.
        var signedIn = CsrfSession.start(mvc, request -> {
            request.setCookies(session);
            return request;
        });
        var again = mvc.post().uri("/webauthn/register/options").with(signedIn).exchange();
        assertThat(again).hasStatusOk();
        assertThat(again).bodyJson().extractingPath("$.user.name").isEqualTo("ana@example.com");
    }

    private MvcTestResult registerOptions(UUID userId, CsrfSession csrf) {
        return mvc.post()
                .uri("/webauthn/register/options")
                .with(TestUsers.signedIn(userId, null))
                .with(csrf)
                .exchange();
    }

    private static ImmutableCredentialRecord credential(Bytes handle, Instant created) {
        return ImmutableCredentialRecord.builder()
                .credentialId(Bytes.random())
                .userEntityUserId(handle)
                .publicKey(new ImmutablePublicKeyCose(new byte[] {1, 2, 3}))
                .transports(Set.of())
                .label("Celular")
                .created(created)
                .lastUsed(created)
                .build();
    }

    private Map<String, Object> sessionRow(String sessionId) {
        return jdbc.sql("select max_inactive_interval, principal_name from spring_session where session_id = :id")
                .param("id", sessionId)
                .query()
                .singleRow();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
