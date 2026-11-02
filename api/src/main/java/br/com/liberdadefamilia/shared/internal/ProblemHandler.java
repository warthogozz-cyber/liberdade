package br.com.liberdadefamilia.shared.internal;

import br.com.liberdadefamilia.shared.DomainException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every failure into an RFC 9457 {@code application/problem+json} response with a stable {@code code} and a
 * pt-BR {@code detail}. Exception messages, class names and stack traces never reach the client.
 */
@RestControllerAdvice
class ProblemHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemHandler.class);

    private static final String INVALID_DETAIL = "Alguns campos precisam de ajuste.";
    private static final String INTERNAL_DETAIL = "Algo deu errado do nosso lado. Tente de novo em instantes.";

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        return ResponseEntity.status(statusCode).headers(headers).body(problem(statusCode.value()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT.value(), "request.invalid", INVALID_DETAIL);
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "code", String.valueOf(error.getCode())))
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .headers(headers)
                .body(problem);
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomain(DomainException ex) {
        return ResponseEntity.status(ex.status()).body(problem(ex.status(), ex.code(), ex.detail()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem(500));
    }

    private static ProblemDetail problem(int status) {
        return switch (status) {
            case 400 -> problem(status, "request.malformed", "Não conseguimos ler os dados enviados.");
            case 404 -> problem(status, "not-found", "Não encontramos o que você procurou.");
            case 405 -> problem(status, "method.not-allowed", "Esta ação não é permitida aqui.");
            case 406, 415 -> problem(status, "request.unsupported", "Formato de dados não suportado.");
            case 422 -> problem(status, "request.invalid", INVALID_DETAIL);
            default ->
                status >= 500
                        ? problem(status, "internal", INTERNAL_DETAIL)
                        : problem(status, "request.invalid", INVALID_DETAIL);
        };
    }

    private static ProblemDetail problem(int status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        problem.setProperty("code", code);
        return problem;
    }
}
