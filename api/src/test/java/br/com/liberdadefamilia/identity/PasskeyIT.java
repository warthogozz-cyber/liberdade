package br.com.liberdadefamilia.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.identity.internal.SessionUser;
import br.com.liberdadefamilia.identity.internal.SessionUserAuthentication;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.SoftwareAuthenticator;
import br.com.liberdadefamilia.support.TestUsers;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContext;
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

    /** A passkey sign-in from a new anonymous session must come out under a new id and last as long as a Google one. */
    @Test
    void passkeySignInRotatesTheSessionAndKeepsItFor30Days() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        var anonymous = CsrfSession.start(mvc);
        var login = signInWithPasskey(userId, anonymous);

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

    @Test
    void passkeySignInStoresMinimalContext() throws Exception {
        UUID userId = users.create("Ana Teste", "ana@example.com");

        var login = signInWithPasskey(userId, CsrfSession.start(mvc));

        assertThat(login).hasStatusOk();
        assertThat(login.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                        .filter(header -> header.startsWith(CsrfSession.SESSION_COOKIE + "="))
                        .findFirst()
                        .orElseThrow())
                .contains("Max-Age=2592000");
        Cookie session = login.getResponse().getCookie(CsrfSession.SESSION_COOKIE);
        assertThat(session).isNotNull();
        byte[] stored = jdbc.sql("""
                        select a.attribute_bytes from spring_session_attributes a
                        join spring_session s on s.primary_id = a.session_primary_id
                        where s.session_id = :id and a.attribute_name = 'SPRING_SECURITY_CONTEXT'""")
                .param("id", CsrfSession.sessionIdOf(session))
                .query(byte[].class)
                .single();
        Object context;
        try (var in = new ObjectInputStream(new ByteArrayInputStream(stored))) {
            context = in.readObject();
        }
        assertThat(context)
                .isInstanceOfSatisfying(
                        SecurityContext.class,
                        restored -> assertThat(restored.getAuthentication())
                                .isEqualTo(new SessionUserAuthentication(new SessionUser(userId))));
    }

    @Test
    void registrationWithTamperedAttestationIsRejected() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        var authenticator = new SoftwareAuthenticator("localhost", WEB_ORIGIN);
        var google = CsrfSession.start(mvc, TestUsers.signedIn(userId, null));
        String options = body(registerOptions(userId, google));

        var result = register(userId, google, withOtherChallenge(authenticator.register(options, "Celular")));

        assertRejected(result);
        assertThat(userCredentials.findByUserId(Bytes.fromBase64(JsonPath.read(options, "$.user.id"))))
                .isEmpty();
    }

    /** Unreadable JSON, a credential missing its response, and client data that is not JSON. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"publicKey\":",
                "{\"publicKey\":{\"credential\":{\"id\":\"AA\",\"rawId\":\"AA\",\"response\":{},"
                        + "\"type\":\"public-key\"},\"label\":\"Celular\"}}",
                "{\"publicKey\":{\"credential\":{\"id\":\"AA\",\"rawId\":\"AA\",\"response\":"
                        + "{\"attestationObject\":\"AA\",\"clientDataJSON\":\"AA\"},\"type\":\"public-key\"},"
                        + "\"label\":\"Celular\"}}"
            })
    void registrationWithMalformedBodyIsRejected(String malformed) {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        var google = CsrfSession.start(mvc, TestUsers.signedIn(userId, null));
        assertThat(registerOptions(userId, google)).hasStatusOk();

        assertRejected(register(userId, google, malformed));
    }

    @Test
    void registrationWithoutOptionsIsRejected() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        var authenticator = new SoftwareAuthenticator("localhost", WEB_ORIGIN);
        var google = CsrfSession.start(mvc, TestUsers.signedIn(userId, null));
        String neverIssued = "{\"challenge\":\"" + Bytes.random().toBase64UrlString() + "\",\"user\":{\"id\":\""
                + Bytes.random().toBase64UrlString() + "\"}}";

        assertRejected(register(userId, google, authenticator.register(neverIssued, "Celular")));
    }

    @Test
    void passkeySignInOptionsNeedCsrf() {
        var result = mvc.post().uri("/webauthn/authenticate/options").exchange();

        assertThat(result).hasStatus(403).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("csrf.invalid");
    }

    /**
     * The whole ceremony, with a software authenticator: register a passkey while signed in with Google, then sign in
     * with it from the given anonymous session. Answers the sign-in's response.
     */
    private MvcTestResult signInWithPasskey(UUID userId, CsrfSession anonymous) {
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

        var options =
                mvc.post().uri("/webauthn/authenticate/options").with(anonymous).exchange();
        assertThat(options).hasStatusOk();
        return mvc.post()
                .uri("/login/webauthn")
                .with(anonymous)
                .contentType(MediaType.APPLICATION_JSON)
                .content(authenticator.authenticate(body(options)))
                .exchange();
    }

    private MvcTestResult registerOptions(UUID userId, CsrfSession csrf) {
        return mvc.post()
                .uri("/webauthn/register/options")
                .with(TestUsers.signedIn(userId, null))
                .with(csrf)
                .exchange();
    }

    private MvcTestResult register(UUID userId, CsrfSession csrf, String content) {
        return mvc.post()
                .uri("/webauthn/register")
                .with(TestUsers.signedIn(userId, null))
                .with(csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content)
                .exchange();
    }

    private static void assertRejected(MvcTestResult result) {
        assertThat(result).hasStatus(400).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("passkey.rejected");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Não foi possível criar a passkey. Tente de novo.");
    }

    /** The registration with its client data answering a challenge the server never issued. */
    private static String withOtherChallenge(String registration) {
        String path = "$.publicKey.credential.response.clientDataJSON";
        Bytes clientData = Bytes.fromBase64(JsonPath.read(registration, path));
        String forged = JsonPath.parse(new String(clientData.getBytes(), StandardCharsets.UTF_8))
                .set("$.challenge", Bytes.random().toBase64UrlString())
                .jsonString();
        return JsonPath.parse(registration)
                .set(path, new Bytes(forged.getBytes(StandardCharsets.UTF_8)).toBase64UrlString())
                .jsonString();
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
