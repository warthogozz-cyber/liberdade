package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.LocationTooPreciseException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the Feira's location errors into RFC 9457 problems with a stable {@code code}. It only sees the controllers of
 * this module, and runs before the general handler, which would otherwise answer 500 for these exceptions.
 */
@RestControllerAdvice(basePackages = "br.com.liberdadefamilia.markets")
@Order(Ordered.HIGHEST_PRECEDENCE)
class MarketsExceptionHandler {

    @ExceptionHandler(LocationTooPreciseException.class)
    ResponseEntity<ProblemDetail> handleTooPrecise(LocationTooPreciseException ex) {
        return unprocessable(
                "markets.location_too_precise", "Esta localização é mais precisa do que o app aceita aqui.");
    }

    @ExceptionHandler(LocationInvalidException.class)
    ResponseEntity<ProblemDetail> handleInvalid(LocationInvalidException ex) {
        return unprocessable("markets.location_invalid", "Essa localização não existe. Confira latitude e longitude.");
    }

    @ExceptionHandler(InstagramUnavailableException.class)
    ResponseEntity<ProblemDetail> handleInstagramUnavailable(InstagramUnavailableException ex) {
        return unprocessable(
                "markets.instagram_unavailable", "Não foi possível mostrar o Instagram aqui. Abra o link.");
    }

    private static ResponseEntity<ProblemDetail> unprocessable(String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, detail);
        problem.setProperty("code", code);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(problem);
    }
}
