package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** What V16 puts in the database: recurring rules, planned transactions and the planning module on by default. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MigrationV16IT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestLedger ledger;

    /** The migration already ran, so a family made now only gets the row by running its backfill statement again. */
    @Test
    void existingFamiliesGetPlanningOn() throws IOException {
        UUID id = jdbc.sql("insert into workspaces (name) values ('Familia Antiga') returning id")
                .query(UUID.class)
                .single();
        assertThat(count("select count(*) from workspace_modules where workspace_id = '" + id + "'"))
                .isZero();

        String script =
                new ClassPathResource("db/migration/V16__recurrences.sql").getContentAsString(StandardCharsets.UTF_8);
        int from = script.indexOf("INSERT INTO workspace_modules");
        jdbc.sql(script.substring(from, script.indexOf(';', from))).update();

        assertThat(jdbc.sql(
                                "select enabled from workspace_modules where workspace_id = :id and module_key = 'planning'")
                        .param("id", id)
                        .query(Boolean.class)
                        .list())
                .containsExactly(true);
    }

    @Test
    void occurrenceIsUniqueEvenWhenDeleted() {
        Family family = family();
        UUID rule = rule(family);
        UUID first = planned(family, rule, LocalDate.of(2026, 12, 5));
        jdbc.sql("update transactions set deleted_at = now() where id = :id")
                .param("id", first)
                .update();

        assertThatThrownBy(() -> planned(family, rule, LocalDate.of(2026, 12, 5)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("transactions_one_per_occurrence");
        // Another day of the same rule is another occurrence.
        planned(family, rule, LocalDate.of(2027, 1, 5));
    }

    @Test
    void ruleWithoutScheduledOnViolatesCheck() {
        Family family = family();
        UUID rule = rule(family);

        assertThatThrownBy(() -> jdbc.sql("""
                                insert into transactions
                                    (id, workspace_id, kind, amount, currency, occurred_on, account_id, category_id,
                                     member_id, created_by, status, recurring_rule_id)
                                values (:id, :ws, 'EXPENSE', 10, 'BRL', '2026-12-05', :account, :category,
                                        :m, :m, 'PLANNED', :rule)""")
                        .param("id", TestIds.v7())
                        .param("ws", family.workspaceId())
                        .param("account", family.wallet())
                        .param("category", family.category())
                        .param("m", family.membershipId())
                        .param("rule", rule)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("transactions_rule_has_scheduled_on");
    }

    @Test
    void auditAcceptsRecurringRule() {
        Family family = family();

        int inserted = jdbc.sql("""
                        insert into audit_events (workspace_id, action, target_type, target_id)
                        values (:ws, 'recurring_rule.archived', 'RECURRING_RULE', :target)""")
                .param("ws", family.workspaceId())
                .param("target", UUID.randomUUID())
                .update();

        assertThat(inserted).isEqualTo(1);
    }

    @Test
    void deletingARuleKeepsItsTransactions() {
        Family family = family();
        UUID rule = rule(family);
        UUID transaction = planned(family, rule, LocalDate.of(2026, 12, 5));

        jdbc.sql("delete from recurring_rules where id = :id").param("id", rule).update();

        assertThat(jdbc.sql("select recurring_rule_id is null from transactions where id = :id")
                        .param("id", transaction)
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
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

    private UUID rule(Family family) {
        return jdbc.sql("""
                        insert into recurring_rules
                            (workspace_id, kind, amount, currency, account_id, category_id, frequency, interval,
                             start_on)
                        values (:ws, 'EXPENSE', 10, 'BRL', :account, :category, 'MONTHLY', 1, '2026-12-05')
                        returning id""")
                .param("ws", family.workspaceId())
                .param("account", family.wallet())
                .param("category", family.category())
                .query(UUID.class)
                .single();
    }

    private UUID planned(Family family, UUID rule, LocalDate on) {
        return ledger.insertPlanned(
                family.workspaceId(),
                family.wallet(),
                family.category(),
                Money.brl("10.00"),
                on,
                rule,
                family.membershipId());
    }

    private record Family(UUID workspaceId, UUID membershipId, UUID wallet, UUID category) {}
}
