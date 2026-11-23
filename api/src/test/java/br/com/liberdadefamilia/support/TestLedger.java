package br.com.liberdadefamilia.support;

import br.com.liberdadefamilia.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Reads the seeded wallet and categories of a family, and writes transactions straight in the database. */
public class TestLedger {

    private final JdbcClient jdbc;

    public TestLedger(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The "Carteira" account every family gets on creation. */
    public UUID walletOf(UUID workspaceId) {
        return jdbc.sql("select id from accounts where workspace_id = :workspaceId and name = 'Carteira'")
                .param("workspaceId", workspaceId)
                .query(UUID.class)
                .single();
    }

    public UUID categoryOf(UUID workspaceId, String kind, String name) {
        return jdbc.sql("""
                        select id
                        from categories
                        where workspace_id = :workspaceId
                          and kind = :kind
                          and name = :name
                          and parent_id is null""")
                .param("workspaceId", workspaceId)
                .param("kind", kind)
                .param("name", name)
                .query(UUID.class)
                .single();
    }

    /** An expense in the family's wallet, in the wallet's currency, made and entered by the given membership. */
    public void expense(UUID workspaceId, UUID membershipId, UUID categoryId, String amount, LocalDate on) {
        int inserted = jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on,
                             account_id, category_id, member_id, created_by)
                        select :id, a.workspace_id, 'EXPENSE', :amount, a.currency, :on,
                               a.id, :categoryId, :membershipId, :membershipId
                        from accounts a
                        where a.workspace_id = :workspaceId
                          and a.name = 'Carteira'""")
                .param("id", TestIds.v7())
                .param("workspaceId", workspaceId)
                .param("amount", new BigDecimal(amount))
                .param("on", on)
                .param("categoryId", categoryId)
                .param("membershipId", membershipId)
                .update();
        if (inserted != 1) {
            // Otherwise a test that expects no expense would pass without the one it meant to add.
            throw new IllegalStateException("The family has no wallet");
        }
    }

    /**
     * A planned expense on the account, dated and scheduled on {@code scheduledOn}, of the rule when there is one;
     * made and entered by the given membership. Returns its id.
     */
    public UUID insertPlanned(
            UUID workspaceId,
            UUID accountId,
            UUID categoryId,
            Money amount,
            LocalDate scheduledOn,
            @Nullable UUID ruleId,
            UUID membershipId) {
        UUID id = TestIds.v7();
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on, account_id, category_id,
                             member_id, created_by, status, recurring_rule_id, scheduled_on)
                        values (:id, :workspaceId, 'EXPENSE', :amount, :currency, :on, :accountId, :categoryId,
                                :membershipId, :membershipId, 'PLANNED', :ruleId, :on)""")
                .param("id", id)
                .param("workspaceId", workspaceId)
                .param("amount", amount.amount())
                .param("currency", amount.currency().getCurrencyCode())
                .param("on", scheduledOn)
                .param("accountId", accountId)
                .param("categoryId", categoryId)
                .param("membershipId", membershipId)
                .param("ruleId", ruleId)
                .update();
        return id;
    }
}
