package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.workspace.Role;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The family's accounts. A balance is computed from the transactions in the same query that reads the account. */
@Component
class AccountRepository {

    /**
     * What one transaction did to account {@code a}: income and transfers in count up, expenses and transfers out
     * count down. Callers leave deleted and planned transactions out. A balance is the initial balance plus the sum of
     * this. The figures for planning reuse it, so a balance there is the one the accounts screen shows.
     */
    static final String MOVEMENT = """
            case
                when t.kind = 'INCOME' then t.amount
                when t.kind = 'EXPENSE' then -t.amount
                when t.to_account_id = a.id then t.amount
                else -t.amount
            end""";

    private static final String SELECT_WITH_BALANCE = """
            select a.id, a.name, a.type, a.currency, a.initial_balance, a.owner_membership_id, a.archived_at,
                   a.is_reserve, a.initial_balance + coalesce(sum(%s), 0) as balance
            from accounts a
            left join transactions t
                   on t.workspace_id = a.workspace_id
                  and (t.account_id = a.id or t.to_account_id = a.id)
                  and t.deleted_at is null
                  and t.status = 'CONFIRMED'
            where a.workspace_id = :workspaceId
              and %s
            """.formatted(MOVEMENT, AccountScope.sqlIncludes("a.id"));

    private final JdbcClient jdbc;

    AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** What a write needs to know about an account: its currency, whether it is archived, and who owns it. */
    record AccountChoice(
            Currency currency, boolean archived, @Nullable UUID ownerMembershipId) {}

    /**
     * The accounts the member may see and record in (D13): for the youth, the accounts whose owner is their membership,
     * archived ones included, read from the database on every request; for every other role, {@link AccountScope#all()}.
     */
    AccountScope scopeFor(WorkspaceMember member) {
        return scopeOf(member.workspaceId(), member.membershipId(), member.role());
    }

    /** The same scope for a membership known by its ids, as the data export needs it. */
    AccountScope scopeOf(UUID workspaceId, UUID membershipId, Role role) {
        if (role != Role.YOUTH) {
            return AccountScope.all();
        }
        return new AccountScope(jdbc.sql("""
                        select id
                        from accounts
                        where workspace_id = :workspaceId
                          and owner_membership_id = :membershipId""")
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .query(UUID.class)
                .set());
    }

    UUID insert(
            UUID workspaceId, String name, AccountType type, Money initialBalance, @Nullable UUID ownerMembershipId) {
        return jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance, owner_membership_id)
                        values (:workspaceId, :name, :type, :currency, :initialBalance, :ownerMembershipId)
                        returning id""")
                .param("workspaceId", workspaceId)
                .param("name", name)
                .param("type", type.name())
                .param("currency", initialBalance.currency().getCurrencyCode())
                .param("initialBalance", initialBalance.amount())
                .param("ownerMembershipId", ownerMembershipId)
                .query(UUID.class)
                .single();
    }

    /** Whether the membership holds any account of the family, archived ones included. */
    boolean holdsAny(UUID workspaceId, UUID membershipId) {
        return jdbc.sql("""
                        select exists (select 1 from accounts
                                       where workspace_id = :workspaceId and owner_membership_id = :membershipId)""")
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .query(Boolean.class)
                .single();
    }

    /** The currency of the family's first account, which is the one its wallet was born with. */
    Optional<Currency> firstCurrency(UUID workspaceId) {
        return jdbc.sql(
                        "select currency from accounts where workspace_id = :workspaceId order by created_at, id limit 1")
                .param("workspaceId", workspaceId)
                .query(String.class)
                .optional()
                .map(Currency::getInstance);
    }

    /**
     * Inserts the account unless an active one of the family already has the name. A plain insert that fails on the
     * unique index would abort the surrounding transaction, so the conflict is answered in the statement itself.
     *
     * @return the new id, or empty when the name was taken
     */
    Optional<UUID> insertIfNameFree(
            UUID workspaceId, String name, AccountType type, Money initialBalance, @Nullable UUID ownerMembershipId) {
        return jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance, owner_membership_id)
                        values (:workspaceId, :name, :type, :currency, :initialBalance, :ownerMembershipId)
                        on conflict do nothing
                        returning id""")
                .param("workspaceId", workspaceId)
                .param("name", name)
                .param("type", type.name())
                .param("currency", initialBalance.currency().getCurrencyCode())
                .param("initialBalance", initialBalance.amount())
                .param("ownerMembershipId", ownerMembershipId)
                .query(UUID.class)
                .optional();
    }

    /**
     * Every account of the family in the scope: the active ones first, then the archived, each group by name and id.
     * The balance counts every transaction of the account, whoever recorded it.
     */
    List<AccountView> allWithBalances(UUID workspaceId, AccountScope scope) {
        return jdbc.sql(SELECT_WITH_BALANCE + """
                        group by a.id
                        order by (a.archived_at is not null), a.name, a.id""")
                .param("workspaceId", workspaceId)
                .param("scopeIds", scope.sqlArray())
                .query(AccountRepository::view)
                .list();
    }

    /**
     * The initial balance of each active account in the scope, by id; what the member sees the family have is this plus
     * what the flows moved.
     */
    Map<UUID, Money> activeInitialBalances(UUID workspaceId, AccountScope scope) {
        Map<UUID, Money> balances = new HashMap<>();
        jdbc.sql("""
                        select id, currency, initial_balance
                        from accounts
                        where workspace_id = :workspaceId
                          and archived_at is null
                          and """ + AccountScope.sqlIncludes("id"))
                .param("workspaceId", workspaceId)
                .param("scopeIds", scope.sqlArray())
                .query(rs -> {
                    Currency currency = Currency.getInstance(rs.getString("currency"));
                    balances.put(
                            rs.getObject("id", UUID.class), Money.of(rs.getBigDecimal("initial_balance"), currency));
                });
        return balances;
    }

    /** The account with its balance, when it is in this family and in the scope. */
    Optional<AccountView> findWithBalance(UUID workspaceId, UUID accountId, AccountScope scope) {
        return jdbc.sql(SELECT_WITH_BALANCE + """
                          and a.id = :accountId
                        group by a.id""")
                .param("workspaceId", workspaceId)
                .param("scopeIds", scope.sqlArray())
                .param("accountId", accountId)
                .query(AccountRepository::view)
                .optional();
    }

    /**
     * The currency and archived state of an account of this family, when it is in the scope: an account out of the
     * scope is as absent as one that does not exist.
     */
    Optional<AccountChoice> choice(UUID workspaceId, UUID accountId, AccountScope scope) {
        return jdbc.sql("""
                        select currency, archived_at is not null as archived, owner_membership_id
                        from accounts
                        where workspace_id = :workspaceId
                          and id = :accountId
                          and """ + AccountScope.sqlIncludes("id"))
                .param("workspaceId", workspaceId)
                .param("scopeIds", scope.sqlArray())
                .param("accountId", accountId)
                .query((rs, row) -> new AccountChoice(
                        Currency.getInstance(rs.getString("currency")),
                        rs.getBoolean("archived"),
                        rs.getObject("owner_membership_id", UUID.class)))
                .optional();
    }

    /** Changes what is not null. Whether the account exists in this family. */
    boolean update(
            UUID workspaceId,
            UUID accountId,
            @Nullable String name,
            @Nullable AccountType type,
            @Nullable Money initialBalance,
            @Nullable Boolean isReserve) {
        return jdbc.sql("""
                        update accounts
                        set name = coalesce(cast(:name as text), name),
                            type = coalesce(cast(:type as text), type),
                            initial_balance = coalesce(cast(:initialBalance as numeric), initial_balance),
                            is_reserve = coalesce(cast(:isReserve as boolean), is_reserve),
                            version = version + 1,
                            updated_at = now()
                        where workspace_id = :workspaceId
                          and id = :accountId""")
                        .param("workspaceId", workspaceId)
                        .param("accountId", accountId)
                        .param("name", name)
                        .param("type", type == null ? null : type.name())
                        .param("initialBalance", initialBalance == null ? null : initialBalance.amount())
                        .param("isReserve", isReserve)
                        .update()
                == 1;
    }

    /** Sets the account's owner, or clears it with null. Whether the account exists in the family. */
    boolean setOwner(UUID workspaceId, UUID accountId, @Nullable UUID ownerMembershipId) {
        return jdbc.sql("""
                        update accounts
                        set owner_membership_id = :ownerMembershipId,
                            version = version + 1,
                            updated_at = now()
                        where workspace_id = :workspaceId
                          and id = :accountId""")
                        .param("workspaceId", workspaceId)
                        .param("accountId", accountId)
                        .param("ownerMembershipId", ownerMembershipId)
                        .update()
                == 1;
    }

    /**
     * Sets the initial balance so that the balance becomes the given one: the balance minus what the transactions
     * moved, computed in the same statement that writes it.
     */
    boolean setBalance(UUID workspaceId, UUID accountId, Money balance) {
        return jdbc.sql("""
                        update accounts a
                        set initial_balance = :balance - coalesce((
                                select sum(%s)
                                from transactions t
                                where t.workspace_id = a.workspace_id
                                  and (t.account_id = a.id or t.to_account_id = a.id)
                                  and t.deleted_at is null
                                  and t.status = 'CONFIRMED'), 0),
                            version = version + 1,
                            updated_at = now()
                        where a.workspace_id = :workspaceId
                          and a.id = :accountId""".formatted(MOVEMENT))
                        .param("workspaceId", workspaceId)
                        .param("accountId", accountId)
                        .param("balance", balance.amount())
                        .update()
                == 1;
    }

    /** Archives the account; false when it was already archived or is not in this family. */
    boolean archive(UUID workspaceId, UUID accountId, Instant now) {
        return jdbc.sql("""
                        update accounts
                        set archived_at = :now, version = version + 1, updated_at = now()
                        where workspace_id = :workspaceId
                          and id = :accountId
                          and archived_at is null""")
                        .param("workspaceId", workspaceId)
                        .param("accountId", accountId)
                        .param("now", Timestamp.from(now))
                        .update()
                == 1;
    }

    /** Brings the account back; false when it was already active or is not in this family. */
    boolean unarchive(UUID workspaceId, UUID accountId) {
        return jdbc.sql("""
                        update accounts
                        set archived_at = null, version = version + 1, updated_at = now()
                        where workspace_id = :workspaceId
                          and id = :accountId
                          and archived_at is not null""")
                        .param("workspaceId", workspaceId)
                        .param("accountId", accountId)
                        .update()
                == 1;
    }

    /**
     * How many accounts of the family are active. It locks them, so that two archivals at once cannot both see another
     * active account and leave the family with none.
     */
    int countActive(UUID workspaceId) {
        return jdbc.sql("""
                        select count(*)
                        from (select 1
                              from accounts
                              where workspace_id = :workspaceId
                                and archived_at is null
                              for update) active""")
                .param("workspaceId", workspaceId)
                .query(Integer.class)
                .single();
    }

    private static AccountView view(ResultSet rs, int row) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        Timestamp archivedAt = rs.getTimestamp("archived_at");
        return new AccountView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                AccountType.valueOf(rs.getString("type")),
                currency,
                Money.of(rs.getBigDecimal("initial_balance"), currency),
                Money.of(rs.getBigDecimal("balance"), currency),
                rs.getObject("owner_membership_id", UUID.class),
                archivedAt == null ? null : archivedAt.toInstant(),
                rs.getBoolean("is_reserve"));
    }
}
