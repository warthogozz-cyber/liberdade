package br.com.liberdadefamilia.ledger;

import br.com.liberdadefamilia.workspace.Role;
import java.util.List;
import java.util.UUID;

/** What the ledger hands over for a data export. Deleted transactions never leave. */
public interface LedgerExport {

    /** Every account of the family, archived ones included. */
    List<ExportedAccount> accounts(UUID workspaceId);

    /** Every category of the family, archived ones included. */
    List<ExportedCategory> categories(UUID workspaceId);

    /** Every transaction of the family, newest first. */
    List<ExportedTransaction> transactions(UUID workspaceId);

    /**
     * The transactions where the membership is the "Quem" or the author, newest first. For a YOUTH membership only the
     * ones the youth can see in the app (D13): those touching an account they own, by the ledger's own scope rule.
     * Other roles get all of them.
     */
    List<ExportedTransaction> transactionsOf(UUID workspaceId, UUID membershipId, Role role);
}
