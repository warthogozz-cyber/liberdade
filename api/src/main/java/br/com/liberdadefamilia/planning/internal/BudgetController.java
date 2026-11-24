package br.com.liberdadefamilia.planning.internal;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The family's monthly spending limits. The youth sees none of it. */
@RestController
class BudgetController {

    private final BudgetService service;

    BudgetController(BudgetService service) {
        this.service = service;
    }

    @GetMapping("/v1/workspaces/{ws}/budgets")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
    BudgetMonthView getBudgets(@RequestParam(required = false) String month, WorkspaceMember member) {
        return service.month(member, BudgetService.parseMonth(month));
    }

    @PutMapping("/v1/workspaces/{ws}/budgets/{month}/{categoryId}")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @ApiResponse(responseCode = "200", description = "The limit was changed")
    @ApiResponse(responseCode = "201", description = "The limit was created")
    ResponseEntity<BudgetLineView> setBudget(
            @PathVariable String month,
            @PathVariable UUID categoryId,
            @Valid @RequestBody SetBudgetRequest request,
            WorkspaceMember member) {
        BudgetService.Saved saved = service.set(member, BudgetService.parseMonth(month), categoryId, request);
        return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(saved.line());
    }

    @DeleteMapping("/v1/workspaces/{ws}/budgets/{month}/{categoryId}")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @ApiResponse(responseCode = "204", description = "Removed, or there was none")
    ResponseEntity<Void> deleteBudget(
            @PathVariable String month, @PathVariable UUID categoryId, WorkspaceMember member) {
        service.delete(member, BudgetService.parseMonth(month), categoryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/v1/workspaces/{ws}/budgets/{month}/copy-previous")
    @RequiresModule(ModuleKey.PLANNING)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    CopyBudgetsView copyPreviousBudgets(@PathVariable String month, WorkspaceMember member) {
        return service.copyPrevious(member, BudgetService.parseMonth(month));
    }
}
