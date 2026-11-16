package br.com.liberdadefamilia.ledger.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.ledger.internal.TransactionService.Recorded;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Records, edits, deletes and restores the family's transactions, and lists them by month. A resend of the same
 * transaction answers 200 with the stored one; there is no {@code Location}.
 */
@RestController
class TransactionController {

    private final TransactionService service;

    TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping("/v1/workspaces/{ws}/transactions")
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH})
    @ApiResponse(
            responseCode = "201",
            description = "Recorded",
            content = @Content(schema = @Schema(implementation = TransactionView.class)))
    @ApiResponse(
            responseCode = "200",
            description = "A resend: the stored transaction",
            content = @Content(schema = @Schema(implementation = TransactionView.class)))
    ResponseEntity<TransactionView> recordTransaction(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody CreateTransactionRequest request,
            WorkspaceMember member) {
        Recorded recorded = service.record(member, idempotencyKey, request);
        return ResponseEntity.status(recorded.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(recorded.transaction());
    }

    @PatchMapping("/v1/workspaces/{ws}/transactions/{id}")
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH})
    @ApiResponse(
            responseCode = "200",
            description = "Edited: the transaction at its new version",
            content = @Content(schema = @Schema(implementation = TransactionView.class)))
    TransactionView updateTransaction(
            @PathVariable UUID id, @Valid @RequestBody UpdateTransactionRequest request, WorkspaceMember member) {
        return service.update(member, id, request);
    }

    @GetMapping("/v1/workspaces/{ws}/transactions/{id}")
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    TransactionView getTransaction(@PathVariable UUID id, WorkspaceMember member) {
        return service.get(member, id);
    }

    @DeleteMapping("/v1/workspaces/{ws}/transactions/{id}")
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH})
    @ApiResponse(responseCode = "204", description = "Deleted, or already deleted")
    ResponseEntity<Void> deleteTransaction(@PathVariable UUID id, WorkspaceMember member) {
        service.delete(member, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/v1/workspaces/{ws}/transactions/{id}/restore")
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH})
    @ApiResponse(
            responseCode = "200",
            description = "Restored, or already active: the transaction as it is",
            content = @Content(schema = @Schema(implementation = TransactionView.class)))
    TransactionView restoreTransaction(@PathVariable UUID id, WorkspaceMember member) {
        return service.restore(member, id);
    }

    /**
     * @param categoryId only this category and its subcategories
     * @param accountId only what leaves or enters this account
     */
    @GetMapping("/v1/workspaces/{ws}/transactions")
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    List<TransactionView> listTransactions(
            @RequestParam YearMonth month,
            @RequestParam(required = false) @Nullable UUID categoryId,
            @RequestParam(required = false) @Nullable UUID accountId,
            WorkspaceMember member) {
        return service.list(member, month, categoryId, accountId);
    }
}
