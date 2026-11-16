package br.com.liberdadefamilia.privacy.internal;

import br.com.liberdadefamilia.identity.CurrentUser;
import br.com.liberdadefamilia.identity.RequiresRecentSignIn;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The consents the signed-in person gave or received. Outside any family, so no {@code @WorkspaceScoped}. */
@RestController
class ConsentController {

    private final ConsentService service;

    ConsentController(ConsentService service) {
        this.service = service;
    }

    @GetMapping("/v1/me/consents")
    List<ConsentView> listMyConsents(CurrentUser user) {
        return service.list(user.id());
    }

    @RequiresRecentSignIn
    @DeleteMapping("/v1/me/consents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeConsent(@PathVariable UUID id, CurrentUser user) {
        service.revoke(user.id(), id);
    }
}
