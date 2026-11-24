package br.com.liberdadefamilia.ledger;

import br.com.liberdadefamilia.workspace.Role;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Whether a member sees an account, by the same rule the ledger's own reads follow (D13). */
public interface AccountVisibility {

    /**
     * Whether the member sees the account, or, for a transfer, the account it enters. Every role but the youth sees
     * every account, and is not looked up; the youth sees the accounts they own.
     */
    boolean canSee(UUID workspaceId, UUID membershipId, Role role, UUID accountId, @Nullable UUID toAccountId);
}
