package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.identity.CurrentUser;
import br.com.liberdadefamilia.identity.Passkeys;
import br.com.liberdadefamilia.identity.RequiresRecentSignIn;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person's own passkeys: see them and remove one. */
@RestController
class PasskeyController {

    private final Passkeys passkeys;

    PasskeyController(Passkeys passkeys) {
        this.passkeys = passkeys;
    }

    @GetMapping("/v1/me/passkeys")
    List<PasskeyView> listMyPasskeys(CurrentUser user) {
        return passkeys.list(user.id()).stream().map(PasskeyView::of).toList();
    }

    @DeleteMapping("/v1/me/passkeys/{id}")
    @RequiresRecentSignIn
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removePasskey(@PathVariable String id, CurrentUser user) {
        passkeys.remove(user.id(), id);
    }
}
