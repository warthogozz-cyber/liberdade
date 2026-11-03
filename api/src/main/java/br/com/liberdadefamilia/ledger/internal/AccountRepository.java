package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.Money;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The family's accounts. A balance is computed from the transactions in the same query that lists the accounts. */
@Component
class AccountRepository {

    private final JdbcClient jdbc;

    AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(UUID workspaceId, String name, AccountType type, Currency currency, UUID ownerMembershipId) {
        jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, owner_membership_id)
                        values (:workspaceId, :name, :type, :currency, :ownerMembershipId)""")
                .param("workspaceId", workspaceId)
                .param("name", name)
                .param("type", type.name())
                .param("currency", currency.getCurrencyCode())
                .param("ownerMembershipId", ownerMembershipId)
                .update();
    }

    /**
     * The accounts that are not archived, by name, each with its initial balance plus income, minus expenses and with
     * transfers out and in. Deleted transactions do not count.
     */
    List<Account> unarchivedWithBalances(UUID workspaceId) {
        return jdbc.sql("""
                        select a.id, a.name, a.type, a.currency,
                               a.initial_balance + coalesce(sum(
                                   case
                                       when t.kind = 'INCOME' then t.amount
                                       when t.kind = 'EXPENSE' then -t.amount
                                       when t.to_account_id = a.id then t.amount
                                       else -t.amount
                                   end), 0) as balance
                        from accounts a
                        left join transactions t
                               on t.workspace_id = a.workspace_id
                              and (t.account_id = a.id or t.to_account_id = a.id)
                              and t.deleted_at is null
                        where a.workspace_id = :workspaceId
                          and a.archived_at is null
                        group by a.id
                        order by a.name, a.id""")
                .param("workspaceId", workspaceId)
                .query((rs, row) -> {
                    Currency currency = Currency.getInstance(rs.getString("currency"));
                    return new Account(
                            rs.getObject("id", UUID.class),
                            rs.getString("name"),
                            AccountType.valueOf(rs.getString("type")),
                            currency,
                            Money.of(rs.getBigDecimal("balance"), currency));
                })
                .list();
    }

    /** The currency of an account of this family that is not archived. */
    Optional<Currency> unarchivedCurrency(UUID workspaceId, UUID accountId) {
        return jdbc.sql("""
                        select currency
                        from accounts
                        where workspace_id = :workspaceId
                          and id = :accountId
                          and archived_at is null""")
                .param("workspaceId", workspaceId)
                .param("accountId", accountId)
                .query((rs, row) -> Currency.getInstance(rs.getString("currency")))
                .optional();
    }

    /** An account as {@code GET /accounts} lists it. */
    record Account(UUID id, String name, AccountType type, Currency currency, Money balance) {}
}
