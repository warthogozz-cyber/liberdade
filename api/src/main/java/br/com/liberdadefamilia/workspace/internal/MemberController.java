package br.com.liberdadefamilia.workspace.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;

import br.com.liberdadefamilia.identity.RecentSignInCheck;
import br.com.liberdadefamilia.identity.RequiresRecentSignIn;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The family's people: everyone reads the list; only an owner changes a role or removes someone. */
@RestController
class MemberController {

    private final MemberService service;
    private final RecentSignInCheck recentSignIn;

    MemberController(MemberService service, RecentSignInCheck recentSignIn) {
        this.service = service;
        this.recentSignIn = recentSignIn;
    }

    @GetMapping("/v1/workspaces/{ws}/members")
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
    List<MemberView> listMembers(WorkspaceMember member) {
        return service.list(member);
    }

    /**
     * Changing a role needs a recent sign-in; a nickname alone does not. Both are this one route, so the check runs
     * in the service, inside the locked transaction, and only when the role differs from the one just read.
     */
    @PatchMapping("/v1/workspaces/{ws}/members/{id}")
    @WorkspaceScoped(allow = OWNER)
    MemberView updateMember(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMemberRequest request,
            WorkspaceMember member,
            HttpServletRequest httpRequest) {
        return service.update(member, id, request, () -> recentSignIn.require(httpRequest));
    }

    @DeleteMapping("/v1/workspaces/{ws}/members/{id}")
    @WorkspaceScoped(allow = OWNER)
    @RequiresRecentSignIn
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMember(@PathVariable UUID id, WorkspaceMember member) {
        service.remove(member, id);
    }
}
