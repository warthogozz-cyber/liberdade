package br.com.liberdadefamilia.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.shared.DomainException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@ExtendWith(OutputCaptureExtension.class)
@WebMvcTest
@Import({ProblemHandler.class, ProblemHandlerTest.ProblemProbeController.class})
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

    static class TestConflictException extends DomainException {
        TestConflictException() {
            super(409, "test.conflict", "Conflito de teste.");
        }
    }

    record NameForm(@NotBlank String name) {}

    @RestController
    static class ProblemProbeController {

        @GetMapping("/test/domain-error")
        String domainError() {
            throw new TestConflictException();
        }

        @GetMapping("/test/missing-var")
        String missingVar(@PathVariable("id") String id) {
            return id;
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("secret-internal-detail");
        }

        @PostMapping("/test/validated")
        String validated(@Valid @RequestBody NameForm form) {
            return form.name();
        }
    }
}
