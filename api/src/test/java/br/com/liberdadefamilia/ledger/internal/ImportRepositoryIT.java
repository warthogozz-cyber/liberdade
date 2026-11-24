package br.com.liberdadefamilia.ledger.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Lives in the internal package because the repository is package-private. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ImportRepositoryIT {

    @Autowired
    ImportRepository imports;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestLedger ledger;

    @Test
    void keyTakenSeesTombstones() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID membership = workspaces.membershipOf(ws, ana);
        UUID otherWs = workspaces.createFor(users.create("Bia Teste", "bia@example.com"));
        String tombstoned = "e".repeat(64);
        String deleted = "f".repeat(64);
        jdbc.sql("insert into import_tombstones (workspace_id, account_id, import_key) values (:ws, :a, :k)")
                .param("ws", ws)
                .param("a", ledger.walletOf(ws))
                .param("k", tombstoned)
                .update();
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on,
                             account_id, category_id, member_id, created_by, deleted_at, import_key)
                        values (:id, :ws, 'EXPENSE', 12.34, 'BRL', '2026-10-02',
                                :account, :category, :m, :m, now(), :key)""")
                .param("id", TestIds.v7())
                .param("ws", ws)
                .param("account", ledger.walletOf(ws))
                .param("category", ledger.categoryOf(ws, "EXPENSE", "Mercado"))
                .param("m", membership)
                .param("key", deleted)
                .update();

        assertThat(imports.keyTaken(ws, tombstoned)).isTrue();
        assertThat(imports.keyTaken(ws, deleted)).isTrue();
        assertThat(imports.keyTaken(otherWs, tombstoned)).isFalse();
        assertThat(imports.keyTaken(otherWs, deleted)).isFalse();
        assertThat(imports.keyTaken(ws, "0".repeat(64))).isFalse();
    }
}
