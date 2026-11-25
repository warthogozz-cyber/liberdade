package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The V19 schema: one import key per family, hex only, and the tombstones that go with their account. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ImportSchemaIT {

    private static final String KEY = "d".repeat(64);

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestLedger ledger;

    private UUID workspaceId;

    @BeforeEach
    void family() {
        workspaceId = family("ana@example.com");
    }

    @Test
    void sameKeyTwiceInAFamilyFails() {
        row(workspaceId, KEY, false);

        assertThatThrownBy(() -> row(workspaceId, KEY, false)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameKeyTwiceFailsAlsoWhenTheFirstIsDeleted() {
        row(workspaceId, KEY, true);

        assertThatThrownBy(() -> row(workspaceId, KEY, false)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameKeyInTwoFamiliesIsFine() {
        UUID other = family("bia@example.com");

        row(workspaceId, KEY, false);
        row(other, KEY, false);

        assertThat(jdbc.sql("select count(*) from transactions where import_key = :key and workspace_id in (:a, :b)")
                        .param("key", KEY)
                        .param("a", workspaceId)
                        .param("b", other)
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
    }

    @Test
    void keyMustBe64Hex() {
        assertThatThrownBy(() -> row(workspaceId, "ABC", false)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> row(workspaceId, "D".repeat(64), false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tombstonesGoWithTheAccount() {
        UUID account = jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency)
                        values (:ws, 'Extra', 'CHECKING', 'BRL') returning id""").param("ws", workspaceId).query(UUID.class).single();
        jdbc.sql("insert into import_tombstones (workspace_id, account_id, import_key) values (:ws, :a, :k)")
                .param("ws", workspaceId)
                .param("a", account)
                .param("k", KEY)
                .update();

        jdbc.sql("delete from accounts where id = :id").param("id", account).update();

        assertThat(jdbc.sql("select count(*) from import_tombstones where account_id = :id")
                        .param("id", account)
                        .query(Long.class)
                        .single())
                .isZero();
    }

    private UUID family(String email) {
        return workspaces.createFor(users.create("Ana Teste", email));
    }

    private void row(UUID ws, String key, boolean deleted) {
        UUID membership = jdbc.sql("select id from memberships where workspace_id = :ws limit 1")
                .param("ws", ws)
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on,
                             account_id, category_id, member_id, created_by, deleted_at, import_key)
                        values (:id, :ws, 'EXPENSE', 12.34, 'BRL', '2026-10-02',
                                :account, :category, :m, :m, case when :deleted then now() end, :key)""")
                .param("id", TestIds.v7())
                .param("ws", ws)
                .param("account", ledger.walletOf(ws))
                .param("category", ledger.categoryOf(ws, "EXPENSE", "Mercado"))
                .param("m", membership)
                .param("deleted", deleted)
                .param("key", key)
                .update();
    }
}
