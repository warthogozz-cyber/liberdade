package br.com.liberdadefamilia.privacy.internal;

import br.com.liberdadefamilia.identity.CurrentUser;
import br.com.liberdadefamilia.identity.RequiresRecentSignIn;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The account's 30-day deletion: ask for it, or take it back. Both stay open during the grace (D23). */
@RestController
class AccountDeletionController {

    private final AccountDeletionService service;

    AccountDeletionController(AccountDeletionService service) {
        this.service = service;
    }

    @RequiresRecentSignIn
    @PostMapping("/v1/me/deletion")
    @ResponseStatus(HttpStatus.ACCEPTED)
    DeletionScheduled requestAccountDeletion(CurrentUser user) {
        return new DeletionScheduled(service.request(user));
    }

    @DeleteMapping("/v1/me/deletion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelAccountDeletion(CurrentUser user) {
        service.cancel(user);
    }
}
