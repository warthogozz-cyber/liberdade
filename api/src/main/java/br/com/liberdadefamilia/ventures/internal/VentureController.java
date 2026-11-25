package br.com.liberdadefamilia.ventures.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The family's projects. Everyone reads them; only the owner and the adults create, change or delete. */
@RestController
class VentureController {

    private final VentureService service;

    VentureController(VentureService service) {
        this.service = service;
    }

    @GetMapping("/v1/workspaces/{ws}/ventures")
    @RequiresModule(ModuleKey.VENTURES)
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
    VenturesView getVentures(WorkspaceMember member) {
        return service.list(member);
    }

    @GetMapping("/v1/workspaces/{ws}/ventures/templates")
    @RequiresModule(ModuleKey.VENTURES)
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
    TemplatesView getTemplates() {
        return service.templates();
    }

    @PostMapping("/v1/workspaces/{ws}/ventures")
    @RequiresModule(ModuleKey.VENTURES)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @ApiResponse(responseCode = "201", description = "The project was created")
    ResponseEntity<VentureView> createVenture(
            @Valid @RequestBody CreateVentureRequest request, WorkspaceMember member) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(member, request));
    }

    @GetMapping("/v1/workspaces/{ws}/ventures/{id}")
    @RequiresModule(ModuleKey.VENTURES)
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
    VentureView getVenture(@PathVariable UUID id, WorkspaceMember member) {
        return service.get(member, id);
    }

    @PutMapping("/v1/workspaces/{ws}/ventures/{id}")
    @RequiresModule(ModuleKey.VENTURES)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    VentureView updateVenture(
            @PathVariable UUID id, @Valid @RequestBody UpdateVentureRequest request, WorkspaceMember member) {
        return service.update(member, id, request);
    }

    @PostMapping("/v1/workspaces/{ws}/ventures/{id}/status")
    @RequiresModule(ModuleKey.VENTURES)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    VentureView changeVentureStatus(
            @PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request, WorkspaceMember member) {
        return service.changeStatus(member, id, request);
    }

    @DeleteMapping("/v1/workspaces/{ws}/ventures/{id}")
    @RequiresModule(ModuleKey.VENTURES)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @ApiResponse(responseCode = "204", description = "Deleted")
    ResponseEntity<Void> deleteVenture(@PathVariable UUID id, WorkspaceMember member) {
        service.delete(member, id);
        return ResponseEntity.noContent().build();
    }
}
