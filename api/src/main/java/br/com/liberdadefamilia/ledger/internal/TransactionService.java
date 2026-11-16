package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.domain.TransactionDateRange;
import br.com.liberdadefamilia.ledger.domain.TransactionKind;
import br.com.liberdadefamilia.ledger.internal.TransactionRepository.StoredTransaction;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.workspace.Memberships;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Records, edits, deletes and restores transactions. Recording is idempotent: the device makes the id, and sending
 * the same transaction again returns the stored one instead of a copy. Editing is guarded by the transaction's {@code
 * version} (optimistic lock). Error messages and logs never carry the amount or the description.
 */
@Service
class TransactionService {

    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final Memberships memberships;
    private final Clock clock;

    TransactionService(
            AccountRepository accounts,
            CategoryRepository categories,
            TransactionRepository transactions,
            Memberships memberships,
            Clock clock) {
        this.accounts = accounts;
        this.categories = categories;
        this.transactions = transactions;
        this.memberships = memberships;
        this.clock = clock;
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
        AccountScope scope = accounts.scopeFor(member);
        UUID memberId = request.memberId() == null ? member.membershipId() : request.memberId();
        Optional<StoredTransaction> stored = transactions.find(workspaceId, request.id(), scope);
        if (stored.isPresent()) {
            return new Recorded(repeated(member, scope, stored.get(), request, memberId), false);
        }

        checkAgainstFamily(member, scope, Proposed.of(request, memberId), null);
        Optional<TransactionView> created =
                transactions.insertIfAbsent(workspaceId, member.membershipId(), memberId, request);
        if (created.isPresent()) {
            return new Recorded(created.get(), true);
        }
        // The id was taken since the lookup: by the other tap of a double tap, by another family, or by a transaction
        // out of the scope, which answers as the other family's would.
        return new Recorded(
                repeated(
                        member,
                        scope,
                        transactions
                                .find(workspaceId, request.id(), scope)
                                .orElseThrow(TransactionIdConflictException::new),
                        request,
                        memberId),
                false);
    }

    /**
     * Replaces the transaction with what the edit screen sends. A stale version wins over an invalid payload: the
     * person first needs to see what changed.
     *
     * @throws LedgerNotFoundException when the family has no such transaction
     * @throws LedgerConflictException when it was deleted, or changed since the screen opened (with {@code current})
     * @throws LedgerRejectedException when a family rule fails
     */
    TransactionView update(WorkspaceMember member, UUID id, UpdateTransactionRequest request) {
        UUID workspaceId = member.workspaceId();
        AccountScope scope = accounts.scopeFor(member);
        UUID caller = member.membershipId();
        StoredTransaction stored =
                transactions.findOwn(workspaceId, id, scope, caller).orElseThrow(TransactionService::notFound);
        checkEditable(stored, request.version());
        UUID memberId = request.memberId() == null ? stored.view().memberId() : request.memberId();
        checkAgainstFamily(member, scope, Proposed.of(request, memberId), stored.view());
        return transactions
                .update(workspaceId, id, scope, caller, memberId, request)
                .orElseThrow(() -> {
                    // Someone else edited or deleted it between the checks and the update.
                    StoredTransaction now = transactions
                            .findOwn(workspaceId, id, scope, caller)
                            .orElseThrow(TransactionService::notFound);
                    checkEditable(now, request.version());
                    return versionConflict(now.view());
                });
    }

    /**
     * One transaction, as the list shows it.
     *
     * @throws LedgerNotFoundException when the family has no such transaction, it was purged, or it is out of the
     *     member's scope (deleted or not)
     * @throws LedgerConflictException when it was deleted
     */
    TransactionView get(WorkspaceMember member, UUID id) {
        StoredTransaction stored = transactions
                .find(member.workspaceId(), id, accounts.scopeFor(member))
                .orElseThrow(TransactionService::notFound);
        if (stored.deleted()) {
            throw new LedgerConflictException("transaction.deleted", "Este lançamento foi excluído.");
        }
        return stored.view();
    }

    /**
     * Deletes the transaction; it stays restorable until the nightly purge. Deleting it again changes nothing.
     *
     * @throws LedgerNotFoundException when the family has no such transaction, it was purged, or the member may not
     *     change it
     */
    void delete(WorkspaceMember member, UUID id) {
        AccountScope scope = accounts.scopeFor(member);
        UUID caller = member.membershipId();
        if (!transactions.softDelete(member.workspaceId(), id, scope, caller, clock.instant())
                && transactions.findOwn(member.workspaceId(), id, scope, caller).isEmpty()) {
            throw notFound();
        }
    }

    /**
     * Brings a deleted transaction back, as it was. An active one comes back unchanged, so a repeated "Desfazer" is
     * harmless. Whether its account was archived since does not matter: restoring is not choosing it again.
     *
     * @throws LedgerNotFoundException when the family has no such transaction, it was purged, or the member may not
     *     change it
     */
    TransactionView restore(WorkspaceMember member, UUID id) {
        AccountScope scope = accounts.scopeFor(member);
        UUID caller = member.membershipId();
        return transactions
                .restore(member.workspaceId(), id, scope, caller)
                .or(() -> transactions
                        .findOwn(member.workspaceId(), id, scope, caller)
                        .map(StoredTransaction::view))
                .orElseThrow(TransactionService::notFound);
    }

    /**
     * The month's transactions in the member's scope, newest first, optionally of one category (with its
     * subcategories) or one account.
     *
     * @throws LedgerRejectedException when the month is out of range
     */
    List<TransactionView> list(
            WorkspaceMember member, YearMonth month, @Nullable UUID categoryId, @Nullable UUID accountId) {
        checkMonth(member, month);
        return transactions.between(
                member.workspaceId(),
                month.atDay(1),
                month.plusMonths(1).atDay(1),
                categoryId,
                accountId,
                accounts.scopeFor(member));
    }

    /**
     * Refuses a month the list and the dashboard do not show: before January 2000, or after the month of the last day a
     * transaction may fall on.
     */
    void checkMonth(WorkspaceMember member, YearMonth month) {
        if (month.isBefore(TransactionDateRange.minMonth())
                || month.isAfter(TransactionDateRange.maxMonth(today(member)))) {
            throw new LedgerRejectedException(
                    "month.out-of-range", "Escolha um mês entre janeiro de 2000 e o próximo ano.");
        }
    }

    /** The family's date now, in its own time zone. */
    private LocalDate today(WorkspaceMember member) {
        return LocalDate.now(clock.withZone(member.timezone()));
    }

    private static void checkEditable(StoredTransaction stored, long version) {
        if (stored.deleted()) {
            throw new LedgerConflictException("transaction.deleted", "Este lançamento foi excluído.");
        }
        if (stored.view().version() != version) {
            throw versionConflict(stored.view());
        }
    }

    private static LedgerConflictException versionConflict(TransactionView current) {
        return new LedgerConflictException(
                "transaction.version-conflict",
                "Este lançamento mudou desde que você abriu.",
                Map.of("current", current));
    }

    private static LedgerNotFoundException notFound() {
        return new LedgerNotFoundException("transaction.not-found", "Lançamento não encontrado.");
    }

    /**
     * The rules a new or edited transaction must pass against the family's data, in order, the first failure deciding.
     * On an edit, {@code current} is the stored transaction: an account or category archived since it was chosen may
     * stay where it is, but cannot be chosen anew (D10). An account out of the member's scope is as absent as one that
     * does not exist, and is checked before who the transaction is for.
     */
    private void checkAgainstFamily(
            WorkspaceMember member, AccountScope scope, Proposed proposed, @Nullable TransactionView current) {
        if (!TransactionDateRange.contains(proposed.occurredOn(), today(member))) {
            throw new LedgerRejectedException(
                    "transaction.date-out-of-range", "Escolha uma data entre 2000 e o próximo ano.");
        }
        checkShape(proposed);

        UUID workspaceId = member.workspaceId();
        Currency accountCurrency =
                usableAccount(workspaceId, scope, proposed.accountId(), current == null ? null : current.accountId());
        UUID toAccountId = proposed.toAccountId();
        Currency destinationCurrency = toAccountId == null
                ? accountCurrency
                : usableAccount(workspaceId, scope, toAccountId, current == null ? null : current.toAccountId());
        UUID categoryId = proposed.categoryId();
        if (categoryId != null) {
            UUID keptCategoryId = current == null ? null : current.categoryId();
            TransactionKind categoryKind = categories
                    .choice(workspaceId, categoryId)
                    .filter(choice -> !choice.archived() || categoryId.equals(keptCategoryId))
                    .map(CategoryRepository.CategoryChoice::kind)
                    .orElseThrow(() -> new LedgerRejectedException(
                            "transaction.category-not-found", "Categoria não encontrada nesta família."));
            if (categoryKind != proposed.kind()) {
                throw new LedgerRejectedException(
                        "transaction.category-kind-mismatch", "Esta categoria não combina com o tipo do lançamento.");
            }
        }
        Currency currency = proposed.amount().currency();
        if (!currency.equals(accountCurrency) || !currency.equals(destinationCurrency)) {
            throw new LedgerRejectedException(
                    "transaction.currency-mismatch", "A moeda do valor não é a mesma da conta.");
        }
        // The member the transaction already has may have left the family since; anyone else must be in it now.
        UUID memberId = proposed.memberId();
        boolean kept = current != null && memberId.equals(current.memberId());
        boolean self = member.membershipId().equals(memberId);
        // The youth records only for themselves (D13): an edit may keep who it already was for, nothing else.
        if (scope.isRestricted() && !self && !kept) {
            throw new LedgerRejectedException(
                    "transaction.member-forbidden", "Você só pode lançar em seu próprio nome.");
        }
        if (!self && !kept && !memberships.isActive(workspaceId, memberId)) {
            throw new LedgerRejectedException("transaction.member-not-found", "Essa pessoa não faz parte da família.");
        }
    }

    /** A transfer has two different accounts and no category; income and expenses have a category and one account. */
    private static void checkShape(Proposed proposed) {
        boolean transfer = proposed.kind() == TransactionKind.TRANSFER;
        boolean shapeFits = transfer
                ? proposed.toAccountId() != null
                        && !proposed.toAccountId().equals(proposed.accountId())
                        && proposed.categoryId() == null
                : proposed.toAccountId() == null;
        if (!shapeFits) {
            throw new LedgerRejectedException(
                    "transaction.transfer-invalid",
                    "Uma transferência precisa de duas contas diferentes e não leva categoria.");
        }
        if (!transfer && proposed.categoryId() == null) {
            throw new LedgerRejectedException("transaction.category-required", "Escolha uma categoria.");
        }
    }

    /**
     * The account's currency, when it is one the transaction may use: in the scope, and active or the one it already
     * had. An account out of the scope gets the same answer as one that does not exist.
     */
    private Currency usableAccount(UUID workspaceId, AccountScope scope, UUID accountId, @Nullable UUID keptAccountId) {
        return accounts.choice(workspaceId, accountId, scope)
                .filter(choice -> !choice.archived() || accountId.equals(keptAccountId))
                .map(AccountRepository.AccountChoice::currency)
                .orElseThrow(() -> new LedgerRejectedException(
                        "transaction.account-not-found", "Conta não encontrada nesta família."));
    }

    /**
     * The stored transaction, when the request repeats it; under the same id, any other content is a conflict. Under a
     * restricted scope, so is a transaction the member did not record: the youth may see it, but did not send it.
     */
    private static TransactionView repeated(
            WorkspaceMember member,
            AccountScope scope,
            StoredTransaction stored,
            CreateTransactionRequest request,
            UUID memberId) {
        if (scope.isRestricted() && !stored.createdBy().equals(member.membershipId())) {
            throw new TransactionIdConflictException();
        }
        if (!sameContent(stored.view(), request, memberId)) {
            throw new TransactionIdConflictException();
        }
        return stored.view();
    }

    /**
     * Whether the request carries the stored transaction's data, the amount compared by value and the member as
     * resolved: a resend that leaves it out matches a transaction recorded for the caller.
     */
    private static boolean sameContent(TransactionView stored, CreateTransactionRequest request, UUID memberId) {
        return stored.kind() == request.kind()
                && stored.memberId().equals(memberId)
                && stored.amount().currency().equals(request.amount().currency())
                && stored.amount().amount().compareTo(request.amount().amount()) == 0
                && stored.occurredOn().equals(request.occurredOn())
                && stored.accountId().equals(request.accountId())
                && Objects.equals(stored.toAccountId(), request.toAccountId())
                && Objects.equals(stored.categoryId(), request.categoryId())
                && Objects.equals(stored.description(), request.description());
    }

    /** A recorded transaction, and whether this request created it. */
    record Recorded(TransactionView transaction, boolean created) {}

    /** What a new or edited transaction would hold, for the family rules. */
    private record Proposed(
            TransactionKind kind,
            Money amount,
            LocalDate occurredOn,
            UUID accountId,
            @Nullable UUID toAccountId,
            @Nullable UUID categoryId,
            UUID memberId) {

        static Proposed of(CreateTransactionRequest request, UUID memberId) {
            return new Proposed(
                    request.kind(),
                    request.amount(),
                    request.occurredOn(),
                    request.accountId(),
                    request.toAccountId(),
                    request.categoryId(),
                    memberId);
        }

        static Proposed of(UpdateTransactionRequest request, UUID memberId) {
            return new Proposed(
                    request.kind(),
                    request.amount(),
                    request.occurredOn(),
                    request.accountId(),
                    request.toAccountId(),
                    request.categoryId(),
                    memberId);
        }
    }
}
