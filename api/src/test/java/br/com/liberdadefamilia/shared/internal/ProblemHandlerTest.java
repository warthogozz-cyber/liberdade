package br.com.liberdadefamilia.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.shared.DomainException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Only the handler and the probes: security and the identity web config stay out of this slice.
@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest(useDefaultFilters = false)
@Import({ProblemHandler.class, ProblemHandlerTest.ProblemProbeController.class})
@AutoConfigureMockMvc(addFilters = false)
class ProblemHandlerTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    MockMvcTester mvc;

    @Test
    void domainExceptionUsesItsOwnStatusCodeAndDetail() {
        var result = mvc.get().uri("/test/domain-error");
        assertThat(result).hasStatus(409).hasContentType(MediaType.parseMediaType(PROBLEM_JSON));
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("test.conflict");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Conflito de teste.");
    }

    @Test
    void copiesDomainProperties() {
        var result = mvc.get().uri("/test/domain-error-with-properties");
        assertThat(result).hasStatus(409).hasContentType(MediaType.parseMediaType(PROBLEM_JSON));
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"code":"test.conflict","detail":"Conflito de teste.","current":{"version":1,"name":"Atual"}}""");
    }

    @Test
    void unexpectedExceptionNeverLeaksInternals() {
        var result = mvc.get().uri("/test/boom").exchange();
        assertThat(result).hasStatus(500).hasContentType(MediaType.parseMediaType(PROBLEM_JSON));
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("internal");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Algo deu errado do nosso lado. Tente de novo em instantes.");
        assertThat(result)
                .bodyText()
                .doesNotContain("secret-internal-detail")
                .doesNotContain("IllegalStateException")
                .doesNotContain("at br.com");
    }

    @Test
    void malformedJsonIs400() {
        var result = mvc.post()
                .uri("/test/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{");
        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.malformed");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Não conseguimos ler os dados enviados.");
    }

    @Test
    void invalidFieldIs422WithFieldErrors() {
        var result = mvc.post()
                .uri("/test/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}");
        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Alguns campos precisam de ajuste.");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("name");
        assertThat(result).bodyJson().extractingPath("$.errors[0].code").isEqualTo("NotBlank");
    }

    @Test
    void methodValidationIs422() {
        assertThat(mvc.get().uri("/probe/param?n=0")).hasStatus(422).bodyJson().isLenientlyEqualTo("""
                        {"code":"request.invalid","errors":[{"field":"n","code":"Min"}]}""");
    }

    @Test
    void fieldErrorWithoutCodeIsInvalid() {
        assertThat(ProblemHandler.fieldError("name", null)).containsEntry("code", "invalid");
    }

    @Test
    void unsupportedContentTypeIs415() {
        var result = mvc.post()
                .uri("/test/validated")
                .contentType(MediaType.TEXT_PLAIN)
                .content("x");
        assertThat(result).hasStatus(415);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.unsupported");
    }

    @Test
    void unknownRouteIs404() {
        var result = mvc.get().uri("/nope").exchange();
        assertThat(result).hasStatus(404).hasContentType(MediaType.parseMediaType(PROBLEM_JSON));
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("not-found");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Não encontramos o que você procurou.");
    }

    @Test
    void wrongMethodIs405() {
        var result = mvc.delete().uri("/test/boom");
        assertThat(result).hasStatus(405).hasContentType(MediaType.parseMediaType(PROBLEM_JSON));
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("method.not-allowed");
    }

    @Test
    void frameworkRaisedServerErrorIsLoggedButStaysGeneric(CapturedOutput output) {
        var result = mvc.get().uri("/test/missing-var").exchange();
        assertThat(result).hasStatus(500).hasContentType(MediaType.parseMediaType(PROBLEM_JSON));
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("internal");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Algo deu errado do nosso lado. Tente de novo em instantes.");
        assertThat(output).contains("MissingPathVariableException");
    }

    @Test
    void authenticationExceptionIs401() {
        var result = mvc.get().uri("/test/unauthenticated").exchange();
        assertThat(result).hasStatus(401).hasContentType(MediaType.parseMediaType(PROBLEM_JSON));
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.required");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Entre para continuar.");
        assertThat(result).bodyText().doesNotContain("secret-auth-detail");
    }

    @Test
    void csrfExceptionIs403() {
        var result = mvc.get().uri("/test/csrf").exchange();
        assertThat(result).hasStatus(403).hasContentType(MediaType.parseMediaType(PROBLEM_JSON));
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("csrf.invalid");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Sua sessão foi atualizada. Tente de novo.");
    }

    @Test
    void accessDeniedIs403() {
        var result = mvc.get().uri("/test/forbidden").exchange();
        assertThat(result).hasStatus(403).hasContentType(MediaType.parseMediaType(PROBLEM_JSON));
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Você não tem permissão para isso.");
        assertThat(result).bodyText().doesNotContain("secret-denied-detail");
    }

    static class TestConflictException extends DomainException {
        TestConflictException() {
            super(409, "test.conflict", "Conflito de teste.");
        }

        TestConflictException(Map<String, Object> properties) {
            super(409, "test.conflict", "Conflito de teste.", properties);
        }
    }

    record Current(long version, String name) {}

    record NameForm(@NotBlank String name) {}

    @RestController
    static class ProblemProbeController {

        @GetMapping("/test/domain-error")
        String domainError() {
            throw new TestConflictException();
        }

        @GetMapping("/test/domain-error-with-properties")
        String domainErrorWithProperties() {
            throw new TestConflictException(Map.of("current", new Current(1, "Atual")));
        }

        @GetMapping("/test/missing-var")
        String missingVar(@PathVariable("id") String id) {
            return id;
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("secret-internal-detail");
        }

        // No @Validated on the class, so Spring MVC's built-in method validation throws
        // HandlerMethodValidationException.
        @GetMapping("/probe/param")
        String param(@RequestParam @Min(1) int n) {
            return "ok";
        }

        @GetMapping("/test/unauthenticated")
        String unauthenticated() {
            throw new BadCredentialsException("secret-auth-detail");
        }

        @GetMapping("/test/csrf")
        String csrf() {
            throw new MissingCsrfTokenException(null);
        }

        @GetMapping("/test/forbidden")
        String forbidden() {
            throw new AccessDeniedException("secret-denied-detail");
        }

        @PostMapping("/test/validated")
        String validated(@Valid @RequestBody NameForm form) {
            return form.name();
        }
    }
}
