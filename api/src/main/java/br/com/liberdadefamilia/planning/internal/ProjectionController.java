package br.com.liberdadefamilia.planning.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;

import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Where the family's balance is heading, and its spending split into fixed and variable. The youth sees none of it. */
@RestController
class ProjectionController {

    private final ProjectionService service;

    ProjectionController(ProjectionService service) {
        this.service = service;
    }

    @GetMapping("/v1/workspaces/{ws}/projection")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
    ProjectionView getProjection(WorkspaceMember member) {
        return service.projection(member);
    }

    @GetMapping("/v1/workspaces/{ws}/spending-split")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
    SpendingSplitView getSpendingSplit(@RequestParam(required = false) String month, WorkspaceMember member) {
        return service.spendingSplit(member, BudgetService.parseMonth(month));
    }
}
