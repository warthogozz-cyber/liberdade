package br.com.liberdadefamilia.ledger.internal;

import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.support.SqlArrayValue;

/**
 * The accounts a member may see and record in (D13). For the youth, the accounts they own, archived ones included; for
 * every other role, {@link #all()}, which does not restrict.
 *
 * <p>An empty set is not {@link #all()}: a youth who owns no account sees nothing. Queries apply the scope in SQL with
 * {@link #sqlArray()} bound to {@code :scopeIds}, where a null array means no filter.
 */
record AccountScope(@Nullable Set<UUID> accountIds) {

    private static final AccountScope ALL = new AccountScope(null);

    AccountScope {
        accountIds = accountIds == null ? null : Set.copyOf(accountIds);
    }

    /** Every account of the family. */
    static AccountScope all() {
        return ALL;
    }

    /** Whether the account is in the scope. */
    boolean includes(UUID accountId) {
        return accountIds == null || accountIds.contains(accountId);
    }

    /** Whether the scope leaves some account out (the youth's); false for {@link #all()}. */
    boolean isRestricted() {
        return accountIds != null;
    }

    /** The ids as a {@code uuid[]} parameter, or null for {@link #all()}: an empty set binds as an empty array. */
    @Nullable
    SqlArrayValue sqlArray() {
        return accountIds == null ? null : new SqlArrayValue("uuid", accountIds.toArray());
    }

    /**
     * The SQL condition that the column holds an account in the scope, with the ids bound to {@code :scopeIds}. True for
     * every row under {@link #all()}; false for a null column under a restricted scope.
     */
    static String sqlIncludes(String column) {
        return "(cast(:scopeIds as uuid[]) is null or " + column + " = any(cast(:scopeIds as uuid[])))";
    }
}
