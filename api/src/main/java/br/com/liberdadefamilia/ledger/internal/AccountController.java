package br.com.liberdadefamilia.ledger.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;

import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
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

/** The family's accounts and their balances: list, create, edit, archive and bring back. */
@RestController
class AccountController {

    private final AccountRepository accounts;
    private final AccountService service;

    AccountController(AccountRepository accounts, AccountService service) {
        this.accounts = accounts;
        this.service = service;
    }

    @GetMapping("/v1/workspaces/{ws}/accounts")
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
    List<AccountView> listAccounts(WorkspaceMember member) {
        return accounts.allWithBalances(member.workspaceId());
    }

    @PostMapping("/v1/workspaces/{ws}/accounts")
    @WorkspaceScoped(allow = {OWNER, ADULT})
    ResponseEntity<AccountView> createAccount(
            @Valid @RequestBody CreateAccountRequest request, WorkspaceMember member) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(member, request));
    }

    @PatchMapping("/v1/workspaces/{ws}/accounts/{id}")
    @WorkspaceScoped(allow = {OWNER, ADULT})
    AccountView updateAccount(
            @PathVariable UUID id, @Valid @RequestBody UpdateAccountRequest request, WorkspaceMember member) {
        return service.update(member, id, request);
    }

    @PostMapping("/v1/workspaces/{ws}/accounts/{id}/archive")
    @WorkspaceScoped(allow = {OWNER, ADULT})
    AccountView archiveAccount(@PathVariable UUID id, WorkspaceMember member) {
        return service.archive(member, id);
    }

    @PostMapping("/v1/workspaces/{ws}/accounts/{id}/unarchive")
    @WorkspaceScoped(allow = {OWNER, ADULT})
    AccountView unarchiveAccount(@PathVariable UUID id, WorkspaceMember member) {
        return service.unarchive(member, id);
    }
}
