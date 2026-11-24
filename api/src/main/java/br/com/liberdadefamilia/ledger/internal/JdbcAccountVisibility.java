package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.AccountVisibility;
import br.com.liberdadefamilia.workspace.Role;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class JdbcAccountVisibility implements AccountVisibility {

    private final AccountRepository accounts;

    JdbcAccountVisibility(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean canSee(UUID workspaceId, UUID membershipId, Role role, UUID accountId, @Nullable UUID toAccountId) {
        if (role != Role.YOUTH) {
            return true;
        }
        AccountScope scope = accounts.scopeOf(workspaceId, membershipId, role);
        return scope.includes(accountId) || (toAccountId != null && scope.includes(toAccountId));
    }
}
