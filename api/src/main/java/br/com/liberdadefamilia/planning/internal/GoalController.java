package br.com.liberdadefamilia.planning.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The family's goals. Everyone reads them; only the owner and the adults create, change or put money in. */
@RestController
class GoalController {

    private final GoalService service;

    GoalController(GoalService service) {
        this.service = service;
    }

    @GetMapping("/v1/workspaces/{ws}/goals")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    GoalsView getGoals(WorkspaceMember member) {
        return service.list(member);
    }

    @PostMapping("/v1/workspaces/{ws}/goals")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @ApiResponse(responseCode = "201", description = "The goal was created")
    ResponseEntity<GoalView> createGoal(@Valid @RequestBody CreateGoalRequest request, WorkspaceMember member) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(member, request));
    }

    @PatchMapping("/v1/workspaces/{ws}/goals/{id}")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    GoalView updateGoal(@PathVariable UUID id, @Valid @RequestBody UpdateGoalRequest request, WorkspaceMember member) {
        return service.update(member, id, request);
    }

    @PostMapping("/v1/workspaces/{ws}/goals/{id}/archive")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @ApiResponse(responseCode = "204", description = "Archived, or it already was")
    ResponseEntity<Void> archiveGoal(@PathVariable UUID id, WorkspaceMember member) {
        service.archive(member, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/v1/workspaces/{ws}/goals/{id}/contributions")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @ApiResponse(responseCode = "201", description = "The contribution was recorded")
    ResponseEntity<ContributionView> createContribution(
            @PathVariable UUID id, @Valid @RequestBody CreateContributionRequest request, WorkspaceMember member) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.contribute(member, id, request));
    }

    @GetMapping("/v1/workspaces/{ws}/goals/{id}/contributions")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    List<ContributionView> getContributions(@PathVariable UUID id, WorkspaceMember member) {
        return service.contributions(member, id);
    }
}
