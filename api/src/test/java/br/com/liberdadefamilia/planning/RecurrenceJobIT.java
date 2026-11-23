package br.com.liberdadefamilia.planning;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.planning.internal.RecurrenceJob;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** The daily job keeps the planned transactions two months ahead. The family is made on 2026-11-23. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecurrenceJobIT.Config.class})
class RecurrenceJobIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    @Autowired
    RecurrenceJob job;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestLedger ledger;

    @Autowired
    TestClock clock;

    @BeforeEach
    void today() {
        clock.freeze(Instant.parse("2026-11-23T12:00:00Z"));
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void jobExtendsTheHorizon() {
        Family family = family("ana@example.com");
        job.run();
        assertThat(dates(family.rule)).containsExactly("2026-11-10", "2026-12-10");

        clock.freeze(Instant.parse("2026-12-02T12:00:00Z"));
        job.run();

        assertThat(dates(family.rule)).containsExactly("2026-11-10", "2026-12-10", "2027-01-10");
    }

    @Test
    void jobTwiceIsIdempotent() {
        Family family = family("ana@example.com");
        clock.freeze(Instant.parse("2026-12-02T12:00:00Z"));

        job.run();
        List<String> first = dates(family.rule);
        job.run();

        assertThat(first).containsExactly("2026-12-10", "2027-01-10");
        assertThat(dates(family.rule)).isEqualTo(first);
    }

    @Test
    void oneFamilyFailingDoesNotStopOthers() {
        Family broken = family("ana@example.com");
        Family healthy = family("bia@example.com");
        // The foreign keys are switched off for this one transaction, so the rule points at an account that is gone.
        tx.executeWithoutResult(status -> {
            jdbc.sql("set local session_replication_role = replica").update();
            jdbc.sql("delete from accounts where id = :id")
                    .param("id", broken.wallet)
                    .update();
        });

        job.run();

        assertThat(dates(broken.rule)).isEmpty();
        assertThat(dates(healthy.rule)).containsExactly("2026-11-10", "2026-12-10");
    }

    private Family family(String email) {
        UUID user = users.create("Teste " + email, email);
        UUID ws = workspaces.createFor(user);
        UUID membership = workspaces.membershipOf(ws, user);
        UUID wallet = ledger.walletOf(ws);
        UUID category = ledger.categoryOf(ws, "EXPENSE", "Mercado");
        UUID rule = jdbc.sql("""
                        insert into recurring_rules
                            (workspace_id, kind, amount, currency, account_id, category_id, frequency, interval,
                             start_on, next_due_on, created_by)
                        values (:ws, 'EXPENSE', 100, 'BRL', :account, :category, 'MONTHLY', 1,
                                date '2026-11-10', date '2026-11-10', :createdBy)
                        returning id""")
                .param("ws", ws)
                .param("account", wallet)
                .param("category", category)
                .param("createdBy", membership)
                .query(UUID.class)
                .single();
        return new Family(ws, wallet, rule);
    }

    private List<String> dates(UUID rule) {
        return jdbc.sql("""
                        select cast(scheduled_on as text) from transactions
                        where recurring_rule_id = :rule and deleted_at is null
                        order by scheduled_on""").param("rule", rule).query(String.class).list();
    }

    private record Family(UUID ws, UUID wallet, UUID rule) {}
}
