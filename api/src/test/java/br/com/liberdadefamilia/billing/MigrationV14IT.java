package br.com.liberdadefamilia.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestUsers;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** What V14 puts in the database: the billing tables, their guarantees and the wider audit CHECK. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MigrationV14IT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @AfterEach
    void dropTestAuditRows() {
        jdbc.sql("delete from audit_events where action = 'TEST'").update();
    }

    private UUID family() {
        return jdbc.sql("insert into workspaces (name) values ('Familia Cobranca') returning id")
                .query(UUID.class)
                .single();
    }

    private UUID order(UUID ws, UUID by, String status) {
        return jdbc.sql("""
                        insert into billing_orders (workspace_id, requested_by, product, amount_cents, status, paid_at)
                        values (:ws, :by, 'FOUNDER_2026', 8900, :status, case when :status = 'PAID' then now() end)
                        returning id""")
                .param("ws", ws)
                .param("by", by)
                .param("status", status)
                .query(UUID.class)
                .single();
    }

    private void audit(String targetType) {
        jdbc.sql("insert into audit_events (action, target_type) values ('TEST', :type)")
                .param("type", targetType)
                .update();
    }

    @Test
    void auditConstraintIsNamedAsTheMigrationDrops() {
        List<String> names = jdbc.sql("""
                        select conname from pg_constraint
                        where conrelid = 'audit_events'::regclass and contype = 'c'""").query(String.class).list();

        assertThat(names).containsExactly("audit_events_target_type_check");
    }

    @Test
    void auditAcceptsOrderAndPlanTargets() {
        audit("ORDER");
        audit("PLAN");
        audit("CONSENT");

        assertThatThrownBy(() -> audit("FOO")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oneOpenOrderPerFamily() {
        UUID ws = family();
        order(ws, null, "CREATED");

        assertThatThrownBy(() -> order(ws, null, "CREATED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("billing_orders_one_open_ux");
        order(ws, null, "CANCELED");
        order(ws, null, "PAID");
    }

    @Test
    void familyDeletionKeepsPaidOrder() {
        UUID owner = users.create("Dona Cobranca", "cobranca@example.com");
        UUID ws = family();
        UUID order = order(ws, owner, "PAID");
        jdbc.sql("""
                        insert into workspace_plans (workspace_id, plan_key, founder, source_order_id, starts_at, ends_at)
                        values (:ws, 'FAMILY', true, :order, now(), now() + interval '1 year')""").param("ws", ws).param("order", order).update();

        jdbc.sql("delete from workspaces where id = :ws").param("ws", ws).update();
        jdbc.sql("delete from users where id = :id").param("id", owner).update();

        assertThat(jdbc.sql("select count(*) from workspace_plans where workspace_id = :ws")
                        .param("ws", ws)
                        .query(Long.class)
                        .single())
                .isZero();
        Map<String, Object> kept = jdbc.sql("select * from billing_orders where id = :id")
                .param("id", order)
                .query()
                .singleRow();
        assertThat(kept.get("workspace_id")).isNull();
        assertThat(kept.get("requested_by")).isNull();
        assertThat(kept.get("amount_cents")).isEqualTo(8900L);
        assertThat(kept.get("paid_at")).isNotNull();
    }
}
