package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.identity.CurrentUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Closes the getting-started card for the signed-in member. It sits outside {@code /v1/workspaces/{ws}}, so it has no
 * {@code @WorkspaceScoped}: the repository's active-membership check is the guard.
 */
@RestController
class GettingStartedController {

    private final WorkspaceRepository workspaces;

    GettingStartedController(WorkspaceRepository workspaces) {
        this.workspaces = workspaces;
    }

    @PostMapping("/v1/me/getting-started/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dismiss(CurrentUser user, @RequestParam UUID workspaceId) {
        if (!workspaces.dismissGettingStarted(workspaceId, user.id())) {
            throw new WorkspaceNotFoundException();
        }
    }
}
