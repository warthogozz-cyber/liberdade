package br.com.liberdadefamilia.privacy.internal;

import static br.com.liberdadefamilia.workspace.Role.OWNER;

import br.com.liberdadefamilia.identity.CurrentUser;
import br.com.liberdadefamilia.identity.RequiresRecentSignIn;
import br.com.liberdadefamilia.workspace.FamilyDeletion;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The family's 30-day deletion, for its Owners (D25). Cancelling reaches the family while it is pending. */
@RestController
class FamilyDeletionController {

    private final FamilyDeletion deletion;

    FamilyDeletionController(FamilyDeletion deletion) {
        this.deletion = deletion;
    }

    @RequiresRecentSignIn
    @PostMapping("/v1/workspaces/{ws}/deletion")
    @WorkspaceScoped(allow = {OWNER})
    @ResponseStatus(HttpStatus.ACCEPTED)
    DeletionScheduled requestFamilyDeletion(WorkspaceMember member, CurrentUser user) {
        return new DeletionScheduled(deletion.schedule(member.workspaceId(), user.id()));
    }

    @DeleteMapping("/v1/workspaces/{ws}/deletion")
    @WorkspaceScoped(
            allow = {OWNER},
            includePendingDeletion = true)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelFamilyDeletion(WorkspaceMember member, CurrentUser user) {
        deletion.cancel(member.workspaceId(), user.id());
    }
}
