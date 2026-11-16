package br.com.liberdadefamilia.ledger.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountScopeTest {

    private final UUID wallet = UUID.randomUUID();
    private final UUID corrente = UUID.randomUUID();

    @Test
    void allIncludesEveryAccountAndBindsNoFilter() {
        AccountScope all = AccountScope.all();

        assertThat(all.includes(wallet)).isTrue();
        assertThat(all.includes(corrente)).isTrue();
        assertThat(all.isRestricted()).isFalse();
        assertThat(all.sqlArray()).isNull();
    }

    @Test
    void ownedIncludesOnlyTheOwnedAccounts() {
        AccountScope owned = new AccountScope(Set.of(wallet));

        assertThat(owned.includes(wallet)).isTrue();
        assertThat(owned.includes(corrente)).isFalse();
        assertThat(owned.isRestricted()).isTrue();
        assertThat(owned.sqlArray()).isNotNull();
    }

    /** A youth with no account sees nothing: the empty set is restricted and binds an empty array, never null. */
    @Test
    void emptyIsNotAll() {
        AccountScope empty = new AccountScope(Set.of());

        assertThat(empty.includes(wallet)).isFalse();
        assertThat(empty.isRestricted()).isTrue();
        assertThat(empty.sqlArray()).isNotNull();
        assertThat(empty).isNotEqualTo(AccountScope.all());
    }

    @Test
    void laterChangesToTheGivenSetDoNotWidenTheScope() {
        Set<UUID> ids = new HashSet<>(Set.of(wallet));
        AccountScope owned = new AccountScope(ids);

        ids.add(corrente);

        assertThat(owned.includes(corrente)).isFalse();
    }

    @Test
    void sqlConditionLetsEveryRowThroughOnlyForANullArray() {
        assertThat(AccountScope.sqlIncludes("t.account_id"))
                .isEqualTo("(cast(:scopeIds as uuid[]) is null or t.account_id = any(cast(:scopeIds as uuid[])))");
    }
}
