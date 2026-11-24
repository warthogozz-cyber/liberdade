package br.com.liberdadefamilia.planning.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;

import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The family's emergency reserve. The youth sees none of it. */
@RestController
class ReserveController {

    private final ReserveService service;

    ReserveController(ReserveService service) {
        this.service = service;
    }

    @GetMapping("/v1/workspaces/{ws}/reserve")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
    ReserveView getReserve(WorkspaceMember member) {
        return service.view(member);
    }

    @PatchMapping("/v1/workspaces/{ws}/reserve")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    ReserveView updateReserve(@RequestBody UpdateReserveRequest request, WorkspaceMember member) {
        return service.update(member, request);
    }
}
