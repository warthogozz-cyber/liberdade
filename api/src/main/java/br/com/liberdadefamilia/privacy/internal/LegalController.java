package br.com.liberdadefamilia.privacy.internal;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Which terms and privacy policy are in force, for anyone: the public pages show that text even without a session.
 * Never cached, so the switch to the texts with billing reaches every page as soon as billing goes on.
 */
@RestController
class LegalController {

    private final DocumentService service;

    LegalController(DocumentService service) {
        this.service = service;
    }

    @GetMapping("/v1/legal/current")
    ResponseEntity<LegalVersions> currentLegalVersions() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.current());
    }
}
