package br.com.liberdadefamilia.ventures.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;

import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The steps of a project. Only the owner and the adults change them. */
@RestController
class VentureStepController {

    private final VentureStepService service;

    VentureStepController(VentureStepService service) {
        this.service = service;
    }

    @PostMapping("/v1/workspaces/{ws}/ventures/{id}/steps")
    @RequiresModule(ModuleKey.VENTURES)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @ApiResponse(responseCode = "201", description = "The step was added at the end")
    ResponseEntity<StepView> addStep(
            @PathVariable UUID id, @Valid @RequestBody CreateStepRequest request, WorkspaceMember member) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.add(member, id, request));
    }

    @PatchMapping("/v1/workspaces/{ws}/ventures/{id}/steps/{stepId}")
    @RequiresModule(ModuleKey.VENTURES)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    StepView updateStep(
            @PathVariable UUID id,
            @PathVariable UUID stepId,
            @Valid @RequestBody UpdateStepRequest request,
            WorkspaceMember member) {
        return service.update(member, id, stepId, request);
    }

    @DeleteMapping("/v1/workspaces/{ws}/ventures/{id}/steps/{stepId}")
    @RequiresModule(ModuleKey.VENTURES)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @ApiResponse(responseCode = "204", description = "Deleted")
    ResponseEntity<Void> deleteStep(@PathVariable UUID id, @PathVariable UUID stepId, WorkspaceMember member) {
        service.delete(member, id, stepId);
        return ResponseEntity.noContent().build();
    }
}
