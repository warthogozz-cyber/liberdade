package br.com.liberdadefamilia.workspace.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;

import br.com.liberdadefamilia.identity.RequiresRecentSignIn;
import br.com.liberdadefamilia.shared.RateLimitedException;
import br.com.liberdadefamilia.shared.RateLimiter;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recovery links, for the people who may bring someone in: OWNER and ADULT, signed in within the last 10 minutes. They
 * draw on the same hourly budget as invitations. Taking the link is the public accept route.
 */
@RestController
class RecoveryController {

    private final InvitationService service;
    private final RateLimiter createLimiter;

    RecoveryController(InvitationService service, @Qualifier("invitationCreateRateLimiter") RateLimiter createLimiter) {
        this.service = service;
        this.createLimiter = createLimiter;
    }

    @PostMapping("/v1/workspaces/{ws}/members/{id}/recovery")
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @RequiresRecentSignIn
    @ResponseStatus(HttpStatus.CREATED)
    InvitationView createRecoveryLink(@PathVariable UUID id, WorkspaceMember member) {
        String key = member.userId().toString();
        if (!createLimiter.tryAcquire(key)) {
            throw new RateLimitedException(createLimiter.retryAfterSeconds(key));
        }
        return service.createRecovery(member, id);
    }
}
