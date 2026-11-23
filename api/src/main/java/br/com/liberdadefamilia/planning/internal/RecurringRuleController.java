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

/** The family's recurring rules: list, create, edit and archive. */
@RestController
class RecurringRuleController {

    private final RecurringRuleService service;

    RecurringRuleController(RecurringRuleService service) {
        this.service = service;
    }

    /** Every role lists; the youth sees the rules that touch an account they own. */
    @GetMapping("/v1/workspaces/{ws}/recurring-rules")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    List<RecurringRuleView> listRecurringRules(WorkspaceMember member) {
        return service.list(member);
    }

    @PostMapping("/v1/workspaces/{ws}/recurring-rules")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @ApiResponse(responseCode = "201", description = "Created, with its first planned transactions generated")
    ResponseEntity<RecurringRuleView> createRecurringRule(
            @Valid @RequestBody CreateRecurringRuleRequest request, WorkspaceMember member) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(member, request));
    }

    @PatchMapping("/v1/workspaces/{ws}/recurring-rules/{id}")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    RecurringRuleView updateRecurringRule(
            @PathVariable UUID id, @Valid @RequestBody UpdateRecurringRuleRequest request, WorkspaceMember member) {
        return service.update(member, id, request);
    }

    @PostMapping("/v1/workspaces/{ws}/recurring-rules/{id}/archive")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @ApiResponse(responseCode = "204", description = "Archived, or already archived")
    ResponseEntity<Void> archiveRecurringRule(@PathVariable UUID id, WorkspaceMember member) {
        service.archive(member, id);
        return ResponseEntity.noContent().build();
    }
}
