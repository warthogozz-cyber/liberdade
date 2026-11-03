package br.com.liberdadefamilia.ledger.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;

import br.com.liberdadefamilia.ledger.internal.AccountRepository.Account;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The family's accounts and their balances. */
@RestController
class AccountController {

    private final AccountRepository accounts;

    AccountController(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/v1/workspaces/{ws}/accounts")
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
    List<Account> accounts(WorkspaceMember member) {
        return accounts.unarchivedWithBalances(member.workspaceId());
    }
}
