package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.util.Currency;
import java.util.Objects;
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
     * Checks the transaction against the family's data, the first failure deciding, and records it for the member.
     *
     * @throws TransactionRejectedException when a check fails
     * @throws TransactionIdConflictException when the id is taken by other data or by another family
     */
    Recorded record(WorkspaceMember member, UUID idempotencyKey, CreateTransactionRequest request) {
        UUID workspaceId = member.workspaceId();
        if (!request.id().equals(idempotencyKey) || request.id().version() != 7) {
            throw new TransactionRejectedException(
                    "transaction.invalid-id", "O identificador do lançamento é inválido.");
        }
        if (request.kind() == TransactionKind.TRANSFER) {
            throw new TransactionRejectedException(
                    "transaction.kind-unsupported", "Transferências ainda não estão disponíveis.");
        }
        Currency accountCurrency = accounts.unarchivedCurrency(workspaceId, request.accountId())
                .orElseThrow(() -> new TransactionRejectedException(
                        "transaction.account-not-found", "Conta não encontrada nesta família."));
        TransactionKind categoryKind = categories
                .unarchivedKind(workspaceId, request.categoryId())
                .orElseThrow(() -> new TransactionRejectedException(
                        "transaction.category-not-found", "Categoria não encontrada nesta família."));
        if (categoryKind != request.kind()) {
            throw new TransactionRejectedException(
                    "transaction.category-kind-mismatch", "Esta categoria não combina com o tipo do lançamento.");
        }
        if (!request.amount().currency().equals(accountCurrency)) {
            throw new TransactionRejectedException(
                    "transaction.currency-mismatch", "A moeda do valor não é a mesma da conta.");
        }

        return transactions
                .insertIfAbsent(workspaceId, member.membershipId(), request)
                .map(created -> new Recorded(created, true))
                .orElseGet(() -> new Recorded(storedCopyOf(workspaceId, request), false));
    }

    /** The stored transaction a resend repeats, looked up in this family only. */
    private TransactionView storedCopyOf(UUID workspaceId, CreateTransactionRequest request) {
        return transactions
                .find(workspaceId, request.id())
                .filter(stored -> sameContent(stored, request))
                .orElseThrow(TransactionIdConflictException::new);
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
