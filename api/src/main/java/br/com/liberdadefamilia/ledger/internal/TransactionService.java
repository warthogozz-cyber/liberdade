package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.PlannedTemplate;
import br.com.liberdadefamilia.ledger.TransactionChanged;
import br.com.liberdadefamilia.ledger.TransactionKind;
import br.com.liberdadefamilia.ledger.domain.TransactionDateRange;
import br.com.liberdadefamilia.ledger.internal.TransactionRepository.Planned;
import br.com.liberdadefamilia.ledger.internal.TransactionRepository.StoredTransaction;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.PaymentMethod;
import br.com.liberdadefamilia.workspace.Memberships;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.Modules;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records, edits, deletes and restores transactions. Recording is idempotent: the device makes the id, and sending
 * the same transaction again returns the stored one instead of a copy. Editing is guarded by the transaction's {@code
 * version} (optimistic lock). Error messages and logs never carry the amount or the description. Every change
 * publishes {@link TransactionChanged} inside its transaction, with the accounts it touched.
 */
@Service
class TransactionService {

    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final TagRepository tagLinks;
    private final TagService tags;
    private final Memberships memberships;
    private final Modules modules;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    TransactionService(
            AccountRepository accounts,
            CategoryRepository categories,
            TransactionRepository transactions,
            TagRepository tagLinks,
            TagService tags,
            Memberships memberships,
            Modules modules,
            Clock clock,
            ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.categories = categories;
        this.transactions = transactions;
        this.tagLinks = tagLinks;
        this.tags = tags;
        this.memberships = memberships;
        this.modules = modules;
        this.clock = clock;
        this.events = events;
    }

    /**
     * Records the transaction for the member. A resend of a stored transaction returns it before the checks against
     * the family's data run, so archiving its account or category since then does not turn the resend into a 422. A
     * new transaction is checked first, the first failure deciding.
     *
     * @throws LedgerRejectedException when a check fails
     * @throws TransactionIdConflictException when the id is taken by other data or by another family, or, for the
     *     youth, by a transaction they did not record
     */
    @Transactional
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
            changed(workspaceId, created.get());
            return new Recorded(saveTags(member, created.get(), request.tags()), true);
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
     * <p>The youth edits only what they recorded with every account in their scope; anything else answers as absent.
     * They record only for themselves (D15), and this method reads D15 at edit time: an edit may keep the
     * {@code memberId} the transaction already has, even another member an adult set on the youth's own row, so that
     * the youth can still fix their own transaction. They can never choose another member anew.
     *
     * @throws LedgerNotFoundException when the family has no such transaction, or the member may not change it
     * @throws LedgerConflictException when it was deleted, or changed since the screen opened (with {@code current})
     * @throws LedgerRejectedException when a family rule fails
     */
    @Transactional
    TransactionView update(WorkspaceMember member, UUID id, UpdateTransactionRequest request) {
        UUID workspaceId = member.workspaceId();
        AccountScope scope = accounts.scopeFor(member);
        UUID caller = member.membershipId();
        StoredTransaction stored =
                transactions.findOwn(workspaceId, id, scope, caller).orElseThrow(TransactionService::notFound);
        checkEditable(stored, request.version());
        checkConfirmed(stored);
        UUID memberId = request.memberId() == null ? stored.view().memberId() : request.memberId();
        checkAgainstFamily(member, scope, Proposed.of(request, memberId), stored.view());
        TransactionView updated = transactions
                .update(workspaceId, id, scope, caller, memberId, request)
                .orElseThrow(() -> {
                    // Someone else edited, deleted or planned it between the checks and the update.
                    StoredTransaction now = transactions
                            .findOwn(workspaceId, id, scope, caller)
                            .orElseThrow(TransactionService::notFound);
                    checkEditable(now, request.version());
                    checkConfirmed(now);
                    return versionConflict(now.view());
                });
        changed(workspaceId, updated);
        TransactionView before = stored.view();
        Set<UUID> touched = new HashSet<>();
        touched.add(updated.accountId());
        if (updated.toAccountId() != null) {
            touched.add(updated.toAccountId());
        }
        if (!touched.contains(before.accountId())
                || (before.toAccountId() != null && !touched.contains(before.toAccountId()))) {
            // The edit moved money off an account: it changed too.
            changed(workspaceId, before);
        }
        List<String> names = request.tags();
        return names == null ? updated.withTags(stored.view().tags()) : saveTags(member, updated, names);
    }

    /**
     * Makes a planned transaction a confirmed one, as it is or with what really happened, and enters it under the
     * member who confirms it ({@code created_by} stays the one who made it). The adjustment follows the rules of a new
     * transaction; an account or category archived since the plan may stay, as in an edit. Under the youth's scope, the
     * transaction and the account are the ones in it, as for an edit.
     *
     * @throws LedgerNotFoundException when the family has no such transaction, it was deleted or skipped, or the member
     *     may not change it
     * @throws LedgerConflictException when it is already confirmed, or changed since the screen opened (with {@code
     *     current})
     * @throws LedgerRejectedException when a family rule fails
     */
    @Transactional
    TransactionView confirm(WorkspaceMember member, UUID id, ConfirmTransactionRequest request) {
        UUID workspaceId = member.workspaceId();
        AccountScope scope = accounts.scopeFor(member);
        StoredTransaction stored =
                transactions.findHeld(workspaceId, id, scope).orElseThrow(TransactionService::notFound);
        checkPlanned(stored, request.version());
        TransactionView planned = stored.view();
        Money amount = request.amount() == null ? planned.amount() : request.amount();
        LocalDate occurredOn = request.occurredOn() == null ? planned.occurredOn() : request.occurredOn();
        UUID accountId = request.accountId() == null ? planned.accountId() : request.accountId();
        UUID memberId = member.membershipId();
        checkAgainstFamily(
                member,
                scope,
                new Proposed(
                        planned.kind(),
                        amount,
                        occurredOn,
                        accountId,
                        planned.toAccountId(),
                        planned.categoryId(),
                        memberId,
                        planned.paymentMethod()),
                planned);
        TransactionView confirmed = transactions
                .confirm(workspaceId, id, scope, memberId, request.version(), amount, occurredOn, accountId)
                .orElseThrow(() -> lostRace(workspaceId, id, scope, request.version()));
        changed(workspaceId, confirmed);
        return confirmed;
    }

    /**
     * Skips a planned transaction: it is deleted, and stays restorable like any deleted one. It stays out of the way of
     * the rule, which never plans the occurrence again.
     *
     * @throws LedgerNotFoundException when the family has no such transaction, it was deleted or skipped already, or
     *     the member may not change it
     * @throws LedgerConflictException when it is already confirmed, or changed since the screen opened (with {@code
     *     current})
     */
    @Transactional
    void skip(WorkspaceMember member, UUID id, SkipTransactionRequest request) {
        UUID workspaceId = member.workspaceId();
        AccountScope scope = accounts.scopeFor(member);
        StoredTransaction stored =
                transactions.findHeld(workspaceId, id, scope).orElseThrow(TransactionService::notFound);
        checkPlanned(stored, request.version());
        if (!transactions.skip(workspaceId, id, scope, request.version(), clock.instant())) {
            throw lostRace(workspaceId, id, scope, request.version());
        }
        changed(workspaceId, stored.view());
    }

    /**
     * Applies the rules of a new transaction to a rule's mold, for the member: the same checks, the same codes. The
     * date is today's, which always passes, since the mold has none.
     */
    void checkTemplate(WorkspaceMember member, PlannedTemplate template) {
        checkAgainstFamily(
                member,
                accounts.scopeFor(member),
                new Proposed(
                        template.kind(),
                        template.amount(),
                        today(member),
                        template.accountId(),
                        template.toAccountId(),
                        template.categoryId(),
                        member.membershipId(),
                        template.paymentMethod()),
                null);
    }

    /**
     * Applies the rules of a new transaction to a statement line for the account, recorded by and for the member, with
     * no payment method: the same checks, the same codes. The caller has already checked the account.
     */
    void checkImportRow(WorkspaceMember member, AccountScope scope, UUID accountId, ImportRow row) {
        checkAgainstFamily(
                member,
                scope,
                new Proposed(
                        row.kind(),
                        row.amount(),
                        row.occurredOn(),
                        accountId,
                        null,
                        row.categoryId(),
                        member.membershipId(),
                        null),
                null);
    }

    /** The answer for a confirm or a skip whose conditional update found no row: what the row is now. */
    private LedgerConflictException lostRace(UUID workspaceId, UUID id, AccountScope scope, long version) {
        StoredTransaction now = transactions.findHeld(workspaceId, id, scope).orElseThrow(TransactionService::notFound);
        checkPlanned(now, version);
        return versionConflict(now.view());
    }

    /** Tells the rest of the app that the transaction changed, with the accounts it touches. */
    private void changed(UUID workspaceId, TransactionView transaction) {
        events.publishEvent(new TransactionChanged(
                workspaceId, transaction.id(), transaction.accountId(), transaction.toAccountId()));
    }

    /** Makes the transaction's tags the ones with these names, and returns it with them. */
    private TransactionView saveTags(
            WorkspaceMember member, TransactionView transaction, @Nullable List<String> names) {
        UUID workspaceId = member.workspaceId();
        tagLinks.replaceLinks(workspaceId, transaction.id(), tags.resolve(member, names));
        return transaction.withTags(tagLinks.namesOf(workspaceId, transaction.id()));
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
     * <p>The own-transaction rule in the UPDATE's WHERE ({@link TransactionRepository#softDelete}) is the primary and
     * only guard: the update runs first, and {@code findOwn} afterwards only chooses between "already deleted" and 404.
     * It is not redundant: removing it lets the youth delete what is not theirs.
     *
     * @throws LedgerNotFoundException when the family has no such transaction, it was purged, or the member may not
     *     change it
     */
    @Transactional
    void delete(WorkspaceMember member, UUID id) {
        AccountScope scope = accounts.scopeFor(member);
        UUID caller = member.membershipId();
        if (transactions.softDelete(member.workspaceId(), id, scope, caller, clock.instant())) {
            // Read in the same transaction, so it is the row this call just deleted.
            transactions
                    .findOwn(member.workspaceId(), id, scope, caller)
                    .ifPresent(deleted -> changed(member.workspaceId(), deleted.view()));
        } else if (transactions.findOwn(member.workspaceId(), id, scope, caller).isEmpty()) {
            throw notFound();
        }
    }

    /**
     * Brings a deleted transaction back, as it was. An active one comes back unchanged, so a repeated "Desfazer" is
     * harmless. Whether its account was archived since does not matter: restoring is not choosing it again.
     *
     * <p>The own-transaction rule in the UPDATE's WHERE ({@link TransactionRepository#restore}) is the primary guard:
     * the update runs first, and {@code findOwn} afterwards only answers for an active transaction or 404. It is not
     * redundant: removing it lets the youth restore what is not theirs.
     *
     * @throws LedgerNotFoundException when the family has no such transaction, it was purged, or the member may not
     *     change it
     */
    @Transactional
    TransactionView restore(WorkspaceMember member, UUID id) {
        AccountScope scope = accounts.scopeFor(member);
        UUID caller = member.membershipId();
        return transactions
                .restore(member.workspaceId(), id, scope, caller)
                .map(restored -> {
                    changed(member.workspaceId(), restored);
                    return restored;
                })
                .or(() -> transactions
                        .findRestorable(member.workspaceId(), id, scope, caller)
                        .map(StoredTransaction::view))
                .orElseThrow(TransactionService::notFound);
    }

    /**
     * The month's transactions in the member's scope, newest first, optionally of one category (with its
     * subcategories), one account, one payment method or one tag. The planned ones come only with the planning module
     * on, and, in the family's current month, those of earlier months that are still waiting come too.
     *
     * @throws LedgerRejectedException when the month is out of range
     */
    List<TransactionView> list(
            WorkspaceMember member,
            YearMonth month,
            @Nullable UUID categoryId,
            @Nullable UUID accountId,
            @Nullable PaymentMethod paymentMethod,
            @Nullable UUID tagId) {
        checkMonth(member, month);
        boolean planning = modules.isEnabled(member.workspaceId(), ModuleKey.PLANNING);
        Planned planned = new Planned(planning, planning && month.equals(YearMonth.from(today(member))));
        return transactions.between(
                member.workspaceId(),
                month.atDay(1),
                month.plusMonths(1).atDay(1),
                categoryId,
                accountId,
                paymentMethod,
                tagId,
                planned,
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

    /** An edit changes a confirmed transaction only: a planned one is confirmed first. */
    private static void checkConfirmed(StoredTransaction stored) {
        if (stored.view().planned()) {
            throw new LedgerRejectedException("transaction.planned", "Confirme o previsto antes de editar.");
        }
    }

    /**
     * What a confirm or a skip needs of the row: still there (a deleted or skipped one is as absent), still planned, at
     * the version the screen had. The first failure decides.
     */
    private static void checkPlanned(StoredTransaction stored, long version) {
        if (stored.deleted()) {
            throw notFound();
        }
        if (!stored.view().planned()) {
            throw new LedgerConflictException("transaction.not-planned", "Este lançamento já foi confirmado.");
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
        if (transfer && proposed.paymentMethod() != null) {
            throw new LedgerRejectedException(
                    "transaction.payment-method-transfer", "Transferência não tem forma de pagamento.");
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
                && Objects.equals(stored.description(), request.description())
                && stored.paymentMethod() == request.paymentMethod()
                && tagKeys(stored.tags()).equals(tagKeys(request.tags()));
    }

    /** The tags as a resend compares them: stripped, lower case, each once. */
    private static Set<String> tagKeys(@Nullable List<String> names) {
        return names == null
                ? Set.of()
                : names.stream().map(TagNamesValidator::key).collect(Collectors.toSet());
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
            UUID memberId,
            @Nullable PaymentMethod paymentMethod) {

        static Proposed of(CreateTransactionRequest request, UUID memberId) {
            return new Proposed(
                    request.kind(),
                    request.amount(),
                    request.occurredOn(),
                    request.accountId(),
                    request.toAccountId(),
                    request.categoryId(),
                    memberId,
                    request.paymentMethod());
        }

        static Proposed of(UpdateTransactionRequest request, UUID memberId) {
            return new Proposed(
                    request.kind(),
                    request.amount(),
                    request.occurredOn(),
                    request.accountId(),
                    request.toAccountId(),
                    request.categoryId(),
                    memberId,
                    request.paymentMethod());
        }
    }
}
