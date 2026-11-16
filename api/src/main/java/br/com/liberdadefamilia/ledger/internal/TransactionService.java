package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.util.Currency;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Records transactions idempotently: the device makes the id, and sending the same transaction again returns the
 * stored one instead of a copy. Error messages and logs never carry the amount or the description.
 */
@Service
class TransactionService {

    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;

    TransactionService(AccountRepository accounts, CategoryRepository categories, TransactionRepository transactions) {
        this.accounts = accounts;
        this.categories = categories;
        this.transactions = transactions;
    }

    /**
     * Records the transaction for the member. A resend of a stored transaction returns it before the checks against
     * the family's data run, so archiving its account or category since then does not turn the resend into a 422. A
     * new transaction is checked first, the first failure deciding.
     *
     * @throws LedgerRejectedException when a check fails
     * @throws TransactionIdConflictException when the id is taken by other data or by another family
     */
    Recorded record(WorkspaceMember member, UUID idempotencyKey, CreateTransactionRequest request) {
        UUID workspaceId = member.workspaceId();
        if (!request.id().equals(idempotencyKey) || request.id().version() != 7) {
            throw new LedgerRejectedException("transaction.invalid-id", "O identificador do lançamento é inválido.");
        }
        Optional<TransactionView> stored = transactions.find(workspaceId, request.id());
        if (stored.isPresent()) {
            return new Recorded(repeated(stored.get(), request), false);
        }

        checkAgainstFamily(workspaceId, request);
        Optional<TransactionView> created = transactions.insertIfAbsent(workspaceId, member.membershipId(), request);
        if (created.isPresent()) {
            return new Recorded(created.get(), true);
        }
        // The id was taken since the lookup: by the other tap of a double tap, or by another family.
        return new Recorded(
                repeated(
                        transactions.find(workspaceId, request.id()).orElseThrow(TransactionIdConflictException::new),
                        request),
                false);
    }

    /** The checks a new transaction must pass, in order, against this family's accounts and categories. */
    private void checkAgainstFamily(UUID workspaceId, CreateTransactionRequest request) {
        if (request.kind() == TransactionKind.TRANSFER) {
            throw new LedgerRejectedException(
                    "transaction.kind-unsupported", "Transferências ainda não estão disponíveis.");
        }
        Currency accountCurrency = accounts.choice(workspaceId, request.accountId())
                .filter(choice -> !choice.archived())
                .map(AccountRepository.AccountChoice::currency)
                .orElseThrow(() -> new LedgerRejectedException(
                        "transaction.account-not-found", "Conta não encontrada nesta família."));
        TransactionKind categoryKind = categories
                .choice(workspaceId, request.categoryId())
                .filter(choice -> !choice.archived())
                .map(CategoryRepository.CategoryChoice::kind)
                .orElseThrow(() -> new LedgerRejectedException(
                        "transaction.category-not-found", "Categoria não encontrada nesta família."));
        if (categoryKind != request.kind()) {
            throw new LedgerRejectedException(
                    "transaction.category-kind-mismatch", "Esta categoria não combina com o tipo do lançamento.");
        }
        if (!request.amount().currency().equals(accountCurrency)) {
            throw new LedgerRejectedException(
                    "transaction.currency-mismatch", "A moeda do valor não é a mesma da conta.");
        }
    }

    /** The stored transaction, when the request repeats it; under the same id, any other content is a conflict. */
    private static TransactionView repeated(TransactionView stored, CreateTransactionRequest request) {
        if (!sameContent(stored, request)) {
            throw new TransactionIdConflictException();
        }
        return stored;
    }

    /** Whether the request carries the stored transaction's data, the amount compared by value. */
    private static boolean sameContent(TransactionView stored, CreateTransactionRequest request) {
        return stored.kind() == request.kind()
                && stored.amount().currency().equals(request.amount().currency())
                && stored.amount().amount().compareTo(request.amount().amount()) == 0
                && stored.occurredOn().equals(request.occurredOn())
                && stored.accountId().equals(request.accountId())
                && Objects.equals(stored.categoryId(), request.categoryId())
                && Objects.equals(stored.description(), request.description());
    }

    /** A recorded transaction, and whether this request created it. */
    record Recorded(TransactionView transaction, boolean created) {}
}
