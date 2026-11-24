package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.AccountChanged;
import br.com.liberdadefamilia.ledger.internal.AccountRepository.AccountChoice;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.workspace.Memberships;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates, edits, archives and brings back the family's accounts. Error messages and logs never carry a name or an
 * amount. A change to an existing account publishes {@link AccountChanged} inside its transaction.
 */
@Service
class AccountService {

    private final AccountRepository accounts;
    private final Memberships memberships;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    AccountService(AccountRepository accounts, Memberships memberships, Clock clock, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.memberships = memberships;
        this.clock = clock;
        this.events = events;
    }

    /**
     * @throws LedgerRejectedException when the currency is not the family's or the owner is not in the family
     * @throws LedgerConflictException when an active account has the name
     */
    @Transactional
    AccountView create(WorkspaceMember member, CreateAccountRequest request) {
        UUID workspaceId = member.workspaceId();
        if (!request.initialBalance().currency().equals(member.baseCurrency())) {
            throw unsupportedCurrency();
        }
        UUID owner = request.ownerMembershipId();
        if (owner != null && !memberships.isActive(workspaceId, owner)) {
            throw new LedgerRejectedException("account.owner-not-found", "Essa pessoa não faz parte da família.");
        }
        UUID id = nameTakenIfDuplicate(
                () -> accounts.insert(workspaceId, request.name(), request.type(), request.initialBalance(), owner));
        return view(member, id);
    }

    /**
     * Applies what the request carries. The currency never changes: a balance in another one is a 422.
     *
     * @throws LedgerNotFoundException when the account is not in the family
     */
    @Transactional
    AccountView update(WorkspaceMember member, UUID accountId, UpdateAccountRequest request) {
        UUID workspaceId = member.workspaceId();
        AccountChoice choice = find(member, accountId);
        Money balance = request.balance();
        for (Money money : new Money[] {request.initialBalance(), balance}) {
            if (money != null && !money.currency().equals(choice.currency())) {
                throw unsupportedCurrency();
            }
        }
        nameTakenIfDuplicate(() -> accounts.update(
                workspaceId, accountId, request.name(), request.type(), request.initialBalance(), request.isReserve()));
        if (balance != null) {
            accounts.setBalance(workspaceId, accountId, balance);
        }
        events.publishEvent(new AccountChanged(workspaceId, accountId));
        return view(member, accountId);
    }

    /**
     * Sets who the account belongs to, or takes the owner away with null. An archived account may still change owner.
     *
     * @throws LedgerNotFoundException when the account is not in the family
     * @throws LedgerRejectedException when the owner is not in the family
     */
    @Transactional
    AccountView setOwner(WorkspaceMember member, UUID accountId, @Nullable UUID ownerMembershipId) {
        UUID workspaceId = member.workspaceId();
        find(member, accountId);
        if (ownerMembershipId != null && !memberships.isActive(workspaceId, ownerMembershipId)) {
            throw new LedgerRejectedException("account.owner-not-found", "Essa pessoa não faz parte da família.");
        }
        accounts.setOwner(workspaceId, accountId, ownerMembershipId);
        return view(member, accountId);
    }

    /**
     * Archives the account. Archiving an archived one changes nothing.
     *
     * @throws LedgerNotFoundException when the account is not in the family
     * @throws LedgerRejectedException when it is the family's last active account
     */
    @Transactional
    AccountView archive(WorkspaceMember member, UUID accountId) {
        UUID workspaceId = member.workspaceId();
        if (!find(member, accountId).archived()) {
            if (accounts.countActive(workspaceId) <= 1) {
                throw new LedgerRejectedException(
                        "account.last-active", "A família precisa de pelo menos uma conta ativa.");
            }
            if (accounts.archive(workspaceId, accountId, clock.instant())) {
                events.publishEvent(new AccountChanged(workspaceId, accountId));
            }
        }
        return view(member, accountId);
    }

    /**
     * Brings the account back. Bringing back an active one changes nothing.
     *
     * @throws LedgerNotFoundException when the account is not in the family
     * @throws LedgerConflictException when an active account has its name now
     */
    @Transactional
    AccountView unarchive(WorkspaceMember member, UUID accountId) {
        UUID workspaceId = member.workspaceId();
        if (find(member, accountId).archived()) {
            if (nameTakenIfDuplicate(() -> accounts.unarchive(workspaceId, accountId))) {
                events.publishEvent(new AccountChanged(workspaceId, accountId));
            }
        }
        return view(member, accountId);
    }

    /** The account, read through the member's scope: only adults reach these routes, and theirs is every account. */
    private AccountChoice find(WorkspaceMember member, UUID accountId) {
        return accounts.choice(member.workspaceId(), accountId, accounts.scopeFor(member))
                .orElseThrow(AccountService::notFound);
    }

    private AccountView view(WorkspaceMember member, UUID accountId) {
        return accounts.findWithBalance(member.workspaceId(), accountId, accounts.scopeFor(member))
                .orElseThrow(AccountService::notFound);
    }

    private static LedgerNotFoundException notFound() {
        return new LedgerNotFoundException("account.not-found", "Conta não encontrada.");
    }

    private static LedgerRejectedException unsupportedCurrency() {
        return new LedgerRejectedException(
                "account.currency-unsupported", "Por enquanto, as contas usam a moeda da família.");
    }

    /** Runs the write; the unique index on active names turns into a 409 that says nothing of the other account. */
    private static <T> T nameTakenIfDuplicate(Supplier<T> write) {
        try {
            return write.get();
        } catch (DuplicateKeyException e) {
            throw new LedgerConflictException("account.name-taken", "Já existe uma conta com esse nome.");
        }
    }
}
