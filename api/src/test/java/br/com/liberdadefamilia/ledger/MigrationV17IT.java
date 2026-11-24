package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** What V17 puts in the database: budgets, goals and their contributions, and the planning flags of the ledger. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MigrationV17IT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestLedger ledger;

    @Test
    void defaultsForExistingRows() {
        Family family = family();

        assertThat(jdbc.sql("select is_reserve from accounts where id = :id")
                        .param("id", family.wallet())
                        .query(Boolean.class)
                        .single())
                .isFalse();
        assertThat(jdbc.sql("select is_fixed from categories where id = :id")
                        .param("id", family.category())
                        .query(Boolean.class)
                        .single())
                .isFalse();
        Map<String, Object> workspace = jdbc.sql(
                        "select reserve_target_months, manual_monthly_cost from workspaces where id = :id")
                .param("id", family.workspaceId())
                .query()
                .singleRow();
        assertThat(((Number) workspace.get("reserve_target_months")).intValue()).isEqualTo(6);
        assertThat(workspace.get("manual_monthly_cost")).isNull();
    }

    @Test
    void checksHold() {
        Family family = family();
        UUID goal = goal(family, "Viagem");

        assertCheckFails(() -> budget(family, "2026-11-02", "100"));
        assertCheckFails(() -> budget(family, "2026-11-01", "0"));
        assertCheckFails(() -> goal(family, "x".repeat(41)));
        assertCheckFails(() -> jdbc.sql("""
                        insert into goal_contributions (workspace_id, goal_id, amount, occurred_on, member_id)
                        values (:ws, :goal, 0, '2026-11-23', :member)""")
                .param("ws", family.workspaceId())
                .param("goal", goal)
                .param("member", family.membershipId())
                .update());
        assertCheckFails(() -> jdbc.sql("update workspaces set reserve_target_months = 25 where id = :id")
                .param("id", family.workspaceId())
                .update());
        // The same rows with valid values go in.
        assertThat(budget(family, "2026-11-01", "100")).isEqualTo(1);
    }

    @Test
    void auditAcceptsGoalTarget() {
        Family family = family();

        int inserted = jdbc.sql("""
                        insert into audit_events (workspace_id, action, target_type, target_id)
                        values (:ws, 'goal.archived', 'GOAL', :target)""")
                .param("ws", family.workspaceId())
                .param("target", UUID.randomUUID())
                .update();

        assertThat(inserted).isEqualTo(1);
    }

    private static void assertCheckFails(Runnable insert) {
        assertThatThrownBy(insert::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("check constraint");
    }

    private int budget(Family family, String month, String limit) {
        return jdbc.sql("""
                        insert into budgets (workspace_id, category_id, month, limit_amount, currency)
                        values (:ws, :category, cast(:month as date), cast(:limit as numeric), 'BRL')""")
                .param("ws", family.workspaceId())
                .param("category", family.category())
                .param("month", month)
                .param("limit", limit)
                .update();
    }

    private UUID goal(Family family, String name) {
        return jdbc.sql("""
                        insert into goals (workspace_id, name, target_amount, currency, icon, created_by)
                        values (:ws, :name, 1000, 'BRL', 'travel', :member)
                        returning id""")
                .param("ws", family.workspaceId())
                .param("name", name)
                .param("member", family.membershipId())
                .query(UUID.class)
                .single();
    }

    private Family family() {
        UUID userId = users.create("Ana Teste", "ana" + UUID.randomUUID() + "@example.com");
        UUID workspaceId = workspaces.createFor(userId);
        return new Family(
                workspaceId,
                workspaces.membershipOf(workspaceId, userId),
                ledger.walletOf(workspaceId),
                ledger.categoryOf(workspaceId, "EXPENSE", "Mercado"));
    }

    private record Family(UUID workspaceId, UUID membershipId, UUID wallet, UUID category) {}
}
