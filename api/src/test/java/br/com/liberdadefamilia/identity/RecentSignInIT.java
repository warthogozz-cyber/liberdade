package br.com.liberdadefamilia.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestUsers;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecentSignInIT.SensitiveController.class})
class RecentSignInIT {

    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");
    private static final String WEB_ORIGIN = "http://localhost:5173";

    @RestController
    static class SensitiveController {

        @GetMapping("/v1/test/sensitive")
        @RequiresRecentSignIn
        String sensitive() {
            return "done";
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestUsers users;

    @Test
    void freshSignInPasses() {
        UUID userId = users.create("Ana Teste", "ana@example.com");

        assertThat(mvc.get().uri("/v1/test/sensitive").with(TestUsers.signedIn(userId, null)))
                .hasStatusOk()
                .hasBodyTextEqualTo("done");
    }

    @Test
    void staleSignInIs403() {
        UUID userId = users.create("Ana Teste", "ana@example.com");

        var result = mvc.get()
                .uri("/v1/test/sensitive")
                .with(TestUsers.signedInMinutesAgo(userId, 11))
                .exchange();

        assertThat(result).hasStatus(403).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.reauthentication-required");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Confirme que é você para continuar.");
    }

    @Test
    void insideTheWindowPasses() {
        UUID userId = users.create("Ana Teste", "ana@example.com");

        assertThat(mvc.get().uri("/v1/test/sensitive").with(TestUsers.signedInMinutesAgo(userId, 9)))
                .hasStatusOk();
    }

    @Test
    void unannotatedIgnoresAge() {
        UUID userId = users.create("Ana Teste", "ana@example.com");

        assertThat(mvc.get().uri("/v1/me").with(TestUsers.signedInMinutesAgo(userId, 60)))
                .hasStatusOk();
    }

    @Test
    void corsAllowsPut() {
        var result = mvc.options()
                .uri("/v1/test/sensitive")
                .header(HttpHeaders.ORIGIN, WEB_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
                .contains("PUT");
    }

    @Test
    void reauthAddsPrompt() {
        assertThat(mvc.get().uri("/oauth2/authorization/google?reauth=true"))
                .hasStatus(302)
                .redirectedUrl()
                .contains("prompt=select_account")
                .contains("code_challenge=");
        assertThat(mvc.get().uri("/oauth2/authorization/google"))
                .hasStatus(302)
                .redirectedUrl()
                .doesNotContain("prompt=");
    }
}
