package br.com.liberdadefamilia.identity.internal;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gives the web app the token it must send back in a header on every POST. */
@RestController
class CsrfController {

    @GetMapping("/v1/csrf")
    CsrfTokenView csrf(CsrfToken token) {
        return new CsrfTokenView(token.getHeaderName(), token.getToken());
    }

    record CsrfTokenView(String headerName, String token) {}
}
