package br.com.liberdadefamilia.ledger.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Records the family's transactions and lists them by month. A resend of the same transaction answers 200 with the
 * stored one; there is no {@code Location}, since no route reads a single transaction yet.
 */
@RestController
class TransactionController {

    private final TransactionService service;
    private final TransactionRepository transactions;

    TransactionController(TransactionService service, TransactionRepository transactions) {
        this.service = service;
        this.transactions = transactions;
    }

    @PostMapping("/v1/workspaces/{ws}/transactions")
    @WorkspaceScoped(allow = {OWNER, ADULT})
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

    @GetMapping("/v1/workspaces/{ws}/transactions")
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
    List<TransactionView> listTransactions(@RequestParam YearMonth month, WorkspaceMember member) {
        return transactions.between(
                member.workspaceId(), month.atDay(1), month.plusMonths(1).atDay(1));
    }
}
