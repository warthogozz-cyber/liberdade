package br.com.liberdadefamilia.ledger.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.ledger.TransactionKind;
import br.com.liberdadefamilia.shared.DomainException;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.time.LocalDate;
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

    /**
     * The race of two imports of the same line, one inserting it and one confirming a planned transaction with it:
     * the key check passed for both, so the update meets the key index. It must answer 409 with no line data, never
     * a 500.
     */
    @Test
    void confirmingWithAKeyTakenMeanwhileIsAConflict() {
        UUID ana = users.create("Ana Teste", "ana.corrida@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID membership = workspaces.membershipOf(ws, ana);
        UUID wallet = ledger.walletOf(ws);
        UUID category = ledger.categoryOf(ws, "EXPENSE", "Mercado");
        String key = "a".repeat(64);
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on,
                             account_id, category_id, member_id, created_by, import_key)
                        values (:id, :ws, 'EXPENSE', 12.34, 'BRL', '2026-10-02',
                                :account, :category, :m, :m, :key)""")
                .param("id", TestIds.v7())
                .param("ws", ws)
                .param("account", wallet)
                .param("category", category)
                .param("m", membership)
                .param("key", key)
                .update();
        UUID planned = TestIds.v7();
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on,
                             account_id, category_id, member_id, created_by, status, scheduled_on)
                        values (:id, :ws, 'EXPENSE', 12.34, 'BRL', '2026-10-02',
                                :account, :category, :m, :m, 'PLANNED', '2026-10-02')""")
                .param("id", planned)
                .param("ws", ws)
                .param("account", wallet)
                .param("category", category)
                .param("m", membership)
                .update();
        ImportRow row = new ImportRow(
                TestIds.v7(),
                TransactionKind.EXPENSE,
                Money.brl("12.34"),
                LocalDate.of(2026, 10, 2),
                "MERCADO",
                category,
                key,
                planned);

        assertThatThrownBy(() -> imports.confirmPlanned(ws, planned, wallet, AccountScope.all(), membership, row))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.status()).isEqualTo(409);
                    assertThat(e.code()).isEqualTo("import.concurrent");
                    assertThat(e.detail())
                            .doesNotContain("MERCADO")
                            .doesNotContain("12")
                            .doesNotContain(key);
                });
    }
}
