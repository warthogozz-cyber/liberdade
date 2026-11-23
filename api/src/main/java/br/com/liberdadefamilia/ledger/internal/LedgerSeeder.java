package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.TransactionKind;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.workspace.MemberJoined;
import br.com.liberdadefamilia.workspace.MemberRoleChanged;
import br.com.liberdadefamilia.workspace.MemberSummary;
import br.com.liberdadefamilia.workspace.Memberships;
import br.com.liberdadefamilia.workspace.Role;
import br.com.liberdadefamilia.workspace.WorkspaceCreated;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Gives a new family its wallet and the default categories (D16, D29). It listens synchronously, inside the
 * transaction that creates the family: if seeding fails, the family is not created either.
 */
@Component
class LedgerSeeder {

    static final String WALLET_NAME = "Carteira";

    private static final String YOUTH_WALLET_PREFIX = "Carteira de ";
    private static final int MAX_WALLET_NAMES = 50;
    private static final Currency DEFAULT_CURRENCY = Currency.getInstance("BRL");

    /** The default expense categories, in the order they show before any use. */
    static final List<DefaultCategory> EXPENSES = List.of(
            new DefaultCategory("🛒", "Mercado"),
            new DefaultCategory("🍽️", "Alimentação fora"),
            new DefaultCategory("🚌", "Transporte"),
            new DefaultCategory("🏠", "Moradia"),
            new DefaultCategory("💊", "Saúde"),
            new DefaultCategory("🎉", "Lazer"),
            new DefaultCategory("💡", "Contas e serviços"),
            new DefaultCategory("🛍️", "Compras"),
            new DefaultCategory("📚", "Educação"),
            new DefaultCategory("🐾", "Pets"),
            new DefaultCategory("🎁", "Presentes e doações"),
            new DefaultCategory("🧾", "Impostos e taxas"),
            new DefaultCategory("📦", "Outros"));

    /** The default income categories, in the order they show before any use. */
    static final List<DefaultCategory> INCOMES = List.of(
            new DefaultCategory("💼", "Salário"),
            new DefaultCategory("✨", "Renda extra"),
            new DefaultCategory("📈", "Rendimentos"),
            new DefaultCategory("↩️", "Reembolsos"),
            new DefaultCategory("🎁", "Presentes"),
            new DefaultCategory("📦", "Outros"));

    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final Memberships memberships;

    LedgerSeeder(AccountRepository accounts, CategoryRepository categories, Memberships memberships) {
        this.accounts = accounts;
        this.categories = categories;
        this.memberships = memberships;
    }

    @EventListener
    void on(WorkspaceCreated event) {
        UUID workspaceId = event.workspaceId();
        accounts.insert(
                workspaceId,
                WALLET_NAME,
                AccountType.CASH,
                Money.zero(event.baseCurrency()),
                event.ownerMembershipId());
        insert(workspaceId, TransactionKind.EXPENSE, EXPENSES);
        insert(workspaceId, TransactionKind.INCOME, INCOMES);
    }

    /**
     * A YOUTH gets a wallet of their own, in the family's currency. The name avoids the ones in use: "Carteira de Leo",
     * then "... 2", "... 3". It listens inside the accepting transaction, so a failure here undoes the joining.
     */
    @EventListener
    void on(MemberJoined event) {
        if (event.role() != Role.YOUTH) {
            return;
        }
        createYouthWallet(event.workspaceId(), event.membershipId(), event.nickname());
    }

    /**
     * An ADULT or a VIEWER who becomes a YOUTH gets the wallet too, unless they already hold an account of the family:
     * going back and forth between roles never piles wallets up. Inside the role-changing transaction, like joining.
     */
    @EventListener
    void on(MemberRoleChanged event) {
        if (event.to() != Role.YOUTH || accounts.holdsAny(event.workspaceId(), event.membershipId())) {
            return;
        }
        String nickname = memberships
                .find(event.workspaceId(), event.membershipId())
                .map(MemberSummary::nickname)
                .orElse(null);
        createYouthWallet(event.workspaceId(), event.membershipId(), nickname == null ? "Jovem" : nickname);
    }

    private void createYouthWallet(UUID workspaceId, UUID membershipId, String nickname) {
        Currency currency = accounts.firstCurrency(workspaceId).orElse(DEFAULT_CURRENCY);
        String base = YOUTH_WALLET_PREFIX + nickname;
        // The insert answers a taken name with no row, so two people joining at once cannot both take one name.
        for (int n = 1; n <= MAX_WALLET_NAMES; n++) {
            String name = n == 1 ? base : base + " " + n;
            if (accounts.insertIfNameFree(workspaceId, name, AccountType.CASH, Money.zero(currency), membershipId)
                    .isPresent()) {
                return;
            }
        }
        throw new IllegalStateException("No free name for a youth wallet");
    }

    private void insert(UUID workspaceId, TransactionKind kind, List<DefaultCategory> defaults) {
        for (int order = 0; order < defaults.size(); order++) {
            DefaultCategory category = defaults.get(order);
            categories.insertDefault(workspaceId, kind, category.name(), category.icon(), order);
        }
    }

    /** @param icon an emoji, kept apart from the name */
    record DefaultCategory(String icon, String name) {}
}
