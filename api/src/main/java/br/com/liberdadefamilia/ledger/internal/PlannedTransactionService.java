package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.PlannedTemplate;
import br.com.liberdadefamilia.ledger.PlannedTransactions;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** The planning module's door into the ledger: the checks of a new transaction, and the planned rows of a rule. */
@Service
class PlannedTransactionService implements PlannedTransactions {

    private final TransactionService service;
    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CategoryRepository categories;

    PlannedTransactionService(
            TransactionService service,
            TransactionRepository transactions,
            AccountRepository accounts,
            CategoryRepository categories) {
        this.service = service;
        this.transactions = transactions;
        this.accounts = accounts;
        this.categories = categories;
    }

    @Override
    public void check(WorkspaceMember actor, PlannedTemplate template) {
        service.checkTemplate(actor, template);
    }

    @Override
    public boolean plan(
            UUID workspaceId, UUID ruleId, LocalDate scheduledOn, PlannedTemplate template, UUID authorMembershipId) {
        if (archived(workspaceId, template)) {
            return false;
        }
        return transactions.insertPlanned(workspaceId, ruleId, scheduledOn, template, authorMembershipId);
    }

    @Override
    public int dropOpenFrom(UUID workspaceId, UUID ruleId, LocalDate from) {
        return transactions.deletePlannedFrom(workspaceId, ruleId, from);
    }

    /** Whether the mold points at an archived account or category; one that is absent is a caller bug. */
    private boolean archived(UUID workspaceId, PlannedTemplate template) {
        boolean archived = accountArchived(workspaceId, template.accountId());
        if (template.toAccountId() != null) {
            archived |= accountArchived(workspaceId, template.toAccountId());
        }
        UUID categoryId = template.categoryId();
        if (categoryId != null) {
            archived |= categories
                    .choice(workspaceId, categoryId)
                    .orElseThrow(() -> new IllegalStateException("The planned category is not in the family"))
                    .archived();
        }
        return archived;
    }

    private boolean accountArchived(UUID workspaceId, UUID accountId) {
        return accounts.choice(workspaceId, accountId, AccountScope.all())
                .orElseThrow(() -> new IllegalStateException("The planned account is not in the family"))
                .archived();
    }
}
