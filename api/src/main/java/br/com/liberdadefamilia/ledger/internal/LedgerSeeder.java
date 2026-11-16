package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.workspace.WorkspaceCreated;
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

    LedgerSeeder(AccountRepository accounts, CategoryRepository categories) {
        this.accounts = accounts;
        this.categories = categories;
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

    private void insert(UUID workspaceId, TransactionKind kind, List<DefaultCategory> defaults) {
        for (int order = 0; order < defaults.size(); order++) {
            DefaultCategory category = defaults.get(order);
            categories.insertDefault(workspaceId, kind, category.name(), category.icon(), order);
        }
    }

    /** @param icon an emoji, kept apart from the name */
    record DefaultCategory(String icon, String name) {}
}
