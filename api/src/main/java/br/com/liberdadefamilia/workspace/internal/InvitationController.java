package br.com.liberdadefamilia.workspace.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;

import br.com.liberdadefamilia.shared.RateLimitedException;
import br.com.liberdadefamilia.shared.RateLimiter;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The family's invitations, for the people who may bring someone in: OWNER and ADULT. Only an OWNER may invite another
 * OWNER; that rule lives in the service.
 */
@RestController
class InvitationController {

    private final InvitationService service;
    private final RateLimiter createLimiter;

    InvitationController(
            InvitationService service, @Qualifier("invitationCreateRateLimiter") RateLimiter createLimiter) {
        this.service = service;
        this.createLimiter = createLimiter;
    }

    @PostMapping("/v1/workspaces/{ws}/invitations")
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @ResponseStatus(HttpStatus.CREATED)
    InvitationView createInvitation(@Valid @RequestBody CreateInvitationRequest request, WorkspaceMember member) {
        String key = member.userId().toString();
        if (!createLimiter.tryAcquire(key)) {
            throw new RateLimitedException(createLimiter.retryAfterSeconds(key));
        }
        return service.create(member, request);
    }

    @GetMapping("/v1/workspaces/{ws}/invitations")
    @WorkspaceScoped(allow = {OWNER, ADULT})
    List<InvitationView> listInvitations(WorkspaceMember member) {
        return service.pending(member);
    }

    @DeleteMapping("/v1/workspaces/{ws}/invitations/{id}")
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeInvitation(@PathVariable UUID id, WorkspaceMember member) {
        service.revoke(member, id);
    }
}
